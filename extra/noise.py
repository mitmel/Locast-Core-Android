import Image, ImageDraw
import random

width = 480
height = 854

im = Image.new("RGB", (width, height))
draw = ImageDraw.Draw(im)

for y in range(0,height,2):
    for x in range(0,width,2):
        v = random.randint(0,256)
        draw.rectangle(((x,y), (x+1,y+1)), fill=(v,v,v))

im.save("noise.png", "PNG")
