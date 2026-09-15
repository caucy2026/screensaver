from pathlib import Path
import subprocess,time,json,hashlib,os
import numpy as np
import torch
from PIL import Image
from srvgg_arch import SRVGGNetCompact
p=Path(__file__).parent
cache=Path(os.environ['SCREENSAVER_MEDIA_CACHE']);cache.mkdir(parents=True,exist_ok=True)
weights=Path(os.environ['REALESRGAN_WEIGHTS']);digest=hashlib.sha256(weights.read_bytes()).hexdigest()
assert digest=='8dc7edb9ac80ccdc30c3a5dca6616509367f05fbc184ad95b731f05bece96292'
assert hashlib.sha256((p/'source.mp4').read_bytes()).hexdigest()=='d2378526cbf512eccb57ad7f947af56b075f084f8f68f5c77c875938a09ee3fe'
torch.set_num_threads(4);assert torch.backends.mps.is_available()
model=SRVGGNetCompact(num_conv=32);d=torch.load(weights,map_location='cpu',weights_only=True)
model.load_state_dict(d.get('params_ema',d.get('params')));model=model.eval().to('mps').half()
f=os.environ.get('FFMPEG','ffmpeg');width,height=1080,1440
reader=subprocess.Popen([f,'-v','error','-xerror','-i',str(p/'source.mp4'),'-vf','crop=1080:1440:0:336','-an','-pix_fmt','rgb24','-f','rawvideo','-'],stdout=subprocess.PIPE)
log=(cache/'lossless-encode.log').open('w')
writer=subprocess.Popen([f,'-v','error','-y','-f','rawvideo','-pix_fmt','rgb24','-s','1920x2560','-r','30','-i','-','-an','-c:v','ffv1','-level','3','-threads','4','-pix_fmt','bgr0',str(cache/'enhanced-logical.mkv')],stdin=subprocess.PIPE,stderr=log)
count=0;start=time.time()
try:
 with torch.inference_mode():
  while True:
   b=reader.stdout.read(width*height*3)
   if not b:break
   assert len(b)==width*height*3
   arr=np.frombuffer(b,np.uint8).reshape(height,width,3)
   hi=Image.new('RGB',(width*4,height*4))
   for y in range(0,height,480):
    for x in range(0,width,540):
     x0=max(0,x-24);y0=max(0,y-24);x1=min(width,x+540+24);y1=min(height,y+480+24)
     t=torch.from_numpy(arr[y0:y1,x0:x1].copy()).permute(2,0,1).unsqueeze(0).to('mps',dtype=torch.float16)/255
     out=model(t).clamp_(0,1).float().cpu()[0].permute(1,2,0).numpy()
     tile=Image.fromarray(np.round(out*255).astype(np.uint8))
     hi.paste(tile.crop(((x-x0)*4,(y-y0)*4,(min(x+540,width)-x0)*4,(min(y+480,height)-y0)*4)),(x*4,y*4));del t,out,tile
   final=hi.resize((1920,2560),Image.Resampling.LANCZOS)
   writer.stdin.write(final.tobytes())
   if count in [0,150,300,450,568]:final.resize((360,480)).save(cache/f'frame-{count:03}.jpg',quality=95)
   count+=1
   if count%30==0 or count==1:print(f'AI frames {count}/569 elapsed {time.time()-start:.1f}s',flush=True)
 writer.stdin.close();assert writer.wait()==0;assert reader.wait()==0;assert count==569
except BaseException:
 reader.kill();writer.kill();raise
(p/'ai-processing.json').write_text(json.dumps({'model':'Real-ESRGAN realesr-general-x4v3','weights_sha256':digest,'native':[1080,1920],'crop':[0,336,1080,1440],'scale':4,'enhanced':[4320,5760],'logical_master':[1920,2560],'resize':'Lanczos','tile':[540,480],'overlap':24,'precision':'MPS FP16','frames':count,'fps':30,'audio':False,'elapsed_seconds':time.time()-start,'note':'AI enlargement does not imply native high-resolution detail.'},indent=2)+'\n')
print('AI master complete',flush=True)
