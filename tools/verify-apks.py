#!/usr/bin/env python3
from pathlib import Path
import hashlib,json,os,subprocess,sys,zipfile
p=Path(__file__).resolve().parents[1]; suffix=sys.argv[1]
version=dict(line.split('=',1) for line in (p/'version.properties').read_text().splitlines())
tools=Path(os.environ['ANDROID_SDK_ROOT'])/'build-tools'/os.environ.get('BUILD_TOOLS_VERSION','35.0.0')
expected='c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8'
reports=[]
for label,package,kind in [('KEMI双屏屏保','com.kemi.dualscreensaver','app')]:
    apk=p/'release'/f"{label}-v{version['SCREENSAVER_VERSION_NAME']}-{suffix}.apk"
    signed=not suffix.endswith('-unsigned')
    if signed:
        cert=subprocess.check_output([str(tools/'apksigner'),'verify','--print-certs',str(apk)],text=True)
        assert expected in cert.lower(),'Platform certificate mismatch'
    metadata=subprocess.check_output([str(tools/'aapt2'),'dump','badging',str(apk)],text=True)
    assert f"package: name='{package}'" in metadata
    assert f"versionCode='{version['SCREENSAVER_VERSION_CODE']}'" in metadata
    name=version['SCREENSAVER_VERSION_NAME']+('-draft' if suffix.startswith('draft') else '')
    assert f"versionName='{name}'" in metadata
    with zipfile.ZipFile(apk) as z:
        for info in z.infolist():
            if not info.filename.startswith('assets/'): continue
            base=info.filename.removeprefix('assets/')
            if base.startswith('ready_'): continue
            assert z.read(info)==(p/'assets'/base).read_bytes(),base
            if base.endswith('.mp4'): assert info.compress_type==zipfile.ZIP_STORED
        assert ('assets/silver_video.mp4' in z.namelist())==True
        assert z.read('assets/ready_1')==b'1'
        for id,stem in [(2,'douyin'),(3,'spring')]:
            present=(p/'assets'/f'{stem}_video.mp4').is_file()
            assert (f'assets/ready_{id}' in z.namelist())==present
            assert (f'assets/preview_{stem}.jpg' in z.namelist())==present
            assert (f'assets/{stem}_video.mp4' in z.namelist())==present
    reports.append({'apk':str(apk),'package':package,'version':name,'code':int(version['SCREENSAVER_VERSION_CODE']),'bytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'certificate_sha256':expected if signed else None})
(p/'release'/f'build-{suffix}.json').write_text(json.dumps(reports,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(reports,ensure_ascii=False,indent=2))
