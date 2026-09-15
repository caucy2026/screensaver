from pathlib import Path
import os,json,subprocess
from PIL import Image
p=Path('/Volumes/ORICO/kemi/kwallpagers/project/kemi-s1-20260825/kwallpagers-douyin-screensaver');art=p/'screensaver/artwork/silver-short-loop';art.mkdir(exist_ok=True)
f='/Users/newlink/miniforge3/bin/ffmpeg';s,e,n=501,572,9;length=e-s+1
plan=dict(source='assets/set32_video.mp4',source_start_frame=s,source_end_frame_inclusive=e,crossfade_frames=n,output_frames=length-n,duration_seconds=(length-n)/30,reason='Same portrait shot with compatible head/body pose; preserve natural speed and original wallpaper source',new_ai_processing=False)
(art/'loop-plan.json').write_text(json.dumps(plan,indent=2)+'\n')
out=p/'screensaver/assets/silver_video.mp4'
r=subprocess.Popen([f,'-v','error','-xerror','-i',str(p/'assets/set32_video.mp4'),'-vf',f'trim=start_frame={s}:end_frame={e+1},setpts=PTS-STARTPTS','-frames:v',str(length),'-pix_fmt','rgb24','-f','rawvideo','-'],stdout=subprocess.PIPE)
w=subprocess.Popen([f,'-v','error','-y','-f','rawvideo','-pix_fmt','rgb24','-s','2560x1920','-r','30','-i','-','-an','-c:v','libx265','-preset','medium','-crf','19','-x265-params','pools=4:frame-threads=2:log-level=error','-pix_fmt','yuv420p','-profile:v','main','-tag:v','hvc1','-color_primaries','bt709','-color_trc','bt709','-colorspace','bt709','-movflags','+faststart',str(out)],stdin=subprocess.PIPE)
head=[];written=0
for i in range(length):
 raw=r.stdout.read(2560*1920*3);assert len(raw)==2560*1920*3
 if i<n:head.append(Image.frombytes('RGB',(2560,1920),raw));continue
 if i>=length-n:
  k=i-(length-n);raw=Image.blend(Image.frombytes('RGB',(2560,1920),raw),head[k],(k+1)/n).tobytes()
 w.stdin.write(raw);written+=1
w.stdin.close();assert r.wait()==0 and w.wait()==0;assert written==63
for name,vf in [('silver_d2.png','crop=1920:1280:0:0'),('silver_d0.png','crop=1920:1280:0:1280'),('preview_silver.jpg','scale=360:480')]:
 subprocess.run([f,'-v','error','-y','-i',str(out),'-vf','transpose=2,'+vf,'-frames:v','1',str(out.parent/name)],check=True)
print('A: 63 frames / 2.1 seconds; posters from final video')
