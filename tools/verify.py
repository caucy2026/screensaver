#!/usr/bin/env python3
from pathlib import Path
import json,sys,struct,os,shutil,subprocess,xml.etree.ElementTree as E
p=Path(__file__).resolve().parents[1]
locales=json.loads((p/'docs/LOCALES.json').read_text())
reference=None
for locale in locales:
    strings={x.attrib['name']:x.text for x in E.parse(p/'res'/locale['resource']/'strings.xml').getroot()}
    assert all(strings.values())
    if reference is None: reference=set(strings)
    assert set(strings)==reference,locale
assert len(locales)==54
missing=[]
for stem in ('silver','douyin','spring'):
    paths=[p/'assets'/name for name in (f'{stem}_video.mp4',f'{stem}_d2.png',f'{stem}_d0.png',f'preview_{stem}.jpg')]
    present=[x.is_file() for x in paths]
    if not any(present): missing.append(stem); continue
    assert all(present),f'Incomplete content: {stem}'
    for path in paths[1:3]:
        b=path.read_bytes(); assert b[:8]==b'\x89PNG\r\n\x1a\n' and struct.unpack('>II',b[16:24])==(1920,1280)
assert 'silver' not in missing,'Required silver content missing'
ffprobe=os.environ.get('FFPROBE') or shutil.which('ffprobe') or '/Users/newlink/miniforge3/bin/ffprobe'
for stem in ('silver','douyin','spring'):
    if stem in missing: continue
    media=json.loads(subprocess.check_output([ffprobe,'-v','error','-show_streams','-show_format','-of','json',str(p/'assets'/f'{stem}_video.mp4')],text=True))
    assert len(media['streams'])==1 and media['streams'][0]['codec_type']=='video',f'{stem}: unexpected audio or stream'
    assert media['streams'][0]['r_frame_rate']=='24/1',f'{stem}: expected 24fps'
    seconds=float(media['format']['duration'])
    assert 0 < seconds <= 12.0, f'{stem}: {seconds}s exceeds 12-second loop limit'
    print(f'PASS: {stem} silent loop {seconds:.3f}s <= 12s')
# The approved first release contains silver and spring; Douyin is deferred.
required_missing=[stem for stem in missing if stem in ('silver','spring')]
if required_missing and not (len(sys.argv)>1 and sys.argv[1]=='1'):
    raise SystemExit('Release blocked: missing content '+', '.join(required_missing)+'. ALLOW_INCOMPLETE=1 only creates a clearly marked draft.')
print('PASS: 54 screen-saver locales; independent assets. Missing:',missing)

ns={'a':'http://schemas.android.com/apk/res/android'}
ui=E.parse(p/'AndroidManifest.xml').getroot(); service=E.parse(p/'AndroidManifest-service.xml').getroot()
assert ui.attrib['package']=='com.kemi.dualscreensaver'
assert service.attrib['package']=='com.kemi.dualscreensaver.service'
dream=service.find('application/service'); assert dream.attrib['{'+ns['a']+'}permission']=='android.permission.BIND_DREAM_SERVICE'
assert service.find('application/service/intent-filter/action').attrib['{'+ns['a']+'}name']=='android.service.dreams.DreamService'
assert service.find('application/receiver') is None
assert service.find('permission') is None
assert 'WallpaperService' not in (p/'AndroidManifest-service.xml').read_text()
print('PASS: separate packages, DreamService binding and no cross-package manual selection')
