from pathlib import Path
import os,json,subprocess
from PIL import Image
p=Path(__file__).parent;cache=Path(os.environ['SCREENSAVER_MEDIA_CACHE']);f=os.environ.get('FFMPEG','ffmpeg')
plan=json.loads((p/'loop-plan.json').read_text());s=plan['source_start_frame'];e=plan['source_end_frame_inclusive'];n=plan['crossfade_frames'];length=e-s+1
output=p.parent.parent/'assets/spring_video.mp4'
reader=subprocess.Popen([f,'-v','error','-xerror','-i',str(cache/'enhanced-logical.mkv'),'-vf',f'trim=start_frame={s}:end_frame={e+1},setpts=PTS-STARTPTS','-frames:v',str(length),'-pix_fmt','rgb24','-f','rawvideo','-'],stdout=subprocess.PIPE)
log=(cache/'final-encode.log').open('w')
writer=subprocess.Popen([f,'-v','warning','-y','-f','rawvideo','-pix_fmt','rgb24','-s','1920x2560','-r','30','-i','-','-vf','transpose=1,scale=out_color_matrix=bt709,format=yuv420p','-an','-c:v','libx265','-preset','medium','-crf','19','-x265-params','pools=4:frame-threads=2','-profile:v','main','-tag:v','hvc1','-color_primaries','bt709','-color_trc','bt709','-colorspace','bt709','-movflags','+faststart',str(output)],stdin=subprocess.PIPE,stderr=log)
head=[];written=0;frame_bytes=1920*2560*3
try:
 for index in range(length):
  raw=reader.stdout.read(frame_bytes);assert len(raw)==frame_bytes
  if index<n:head.append(Image.frombytes('RGB',(1920,2560),raw));continue
  if index>=length-n:
   k=index-(length-n);raw=Image.blend(Image.frombytes('RGB',(1920,2560),raw),head[k],(k+1)/n).tobytes()
  writer.stdin.write(raw);written+=1
 writer.stdin.close();assert reader.wait()==0;assert writer.wait()==0;assert written==plan['output_frames']
except BaseException:
 reader.kill();writer.kill();raise
for name,crop in [('spring_d2.png','crop=1920:1280:0:0'),('spring_d0.png','crop=1920:1280:0:1280'),('preview_spring.jpg','scale=360:480')]:
 subprocess.run([f,'-v','error','-y','-i',str(output),'-vf','transpose=2,'+crop,'-frames:v','1',str(output.parent/name)],check=True)
print('Encoded',written,'frames with final-video-derived posters and preview',flush=True)
