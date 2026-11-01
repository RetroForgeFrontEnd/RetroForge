# Google Play store art: the 512 px icon and the 1024x500 feature graphic, from the launcher's neon RF sign and Orbitron.
# Run from anywhere: python tools/play_store_art.py → docs/play-store/.
from PIL import Image, ImageDraw, ImageFont, ImageFilter
R='F:/RetroForge/'
bg=(0x0B,0x10,0x20)
fg=Image.open(R+'app/src/main/res/drawable-nodpi/ic_launcher_neon.png').convert('RGBA')
# Icon: 512, full-bleed background; the adaptive foreground is 108dp with the 72dp safe zone, so scale it to fill.
icon=Image.new('RGBA',(512,512),bg+(255,))
f=fg.resize((512*108//92,)*2,Image.LANCZOS); o=(512-f.size[0])//2
icon.alpha_composite(f,(o,o)); icon.convert('RGB').save(R+'docs/play-store/icon-512.png')
# Feature graphic 1024x500
W,H=1024,500
g=Image.new('RGBA',(W,H),bg+(255,))
grid=ImageDraw.Draw(g)
for x in range(0,W,32): grid.line([(x,0),(x,H)],fill=(20,30,60,255))
for y in range(0,H,32): grid.line([(0,y),(W,y)],fill=(20,30,60,255))
s=fg.resize((380,380),Image.LANCZOS); g.alpha_composite(s,(20,60))
font=lambda n: ImageFont.truetype(R+'core-ui/src/main/res/font/orbitron.ttf',n)
def neon(text,xy,size,col):
    lay=Image.new('RGBA',(W,H),(0,0,0,0)); d=ImageDraw.Draw(lay); d.text(xy,text,font=font(size),fill=col+(255,))
    glow=lay.filter(ImageFilter.GaussianBlur(10)); g.alpha_composite(glow); g.alpha_composite(glow); 
    d2=ImageDraw.Draw(g); d2.text(xy,text,font=font(size),fill=col+(255,))
neon('RETRO',(400,140),80,(255,140,40)); neon('FORGE',(400,230),80,(60,200,255))
d=ImageDraw.Draw(g); d.text((404,340),'Your games. One front end.',font=font(28),fill=(220,225,240,255))
g.convert('RGB').save(R+'docs/play-store/feature-graphic-1024x500.png')
