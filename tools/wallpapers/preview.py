"""Composites a rendered wallpaper with a mock clock (time behind the mask, date in front)."""
import sys
from PIL import Image, ImageDraw, ImageFont, ImageChops

art_path, mask_path, out_path = sys.argv[1:4]
art = Image.open(art_path).convert("RGB")
w, h = art.size
mask = Image.open(mask_path).convert("L").resize((w, h))

font_path = "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"
digit_h = 0.29 * 1.35 * w  # Large + Tall, as in the app
font = ImageFont.truetype(font_path, int(digit_h / 0.72))
text = "9:41"
bbox = font.getbbox(text)
tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
layer = Image.new("L", (tw + 20, th + 20), 0)
ImageDraw.Draw(layer).text((10 - bbox[0], 10 - bbox[1]), text, font=font, fill=255)
layer = layer.resize((int(layer.width * 0.86), layer.height))
center_y = 0.22 * h
clock = Image.new("L", (w, h), 0)
clock.paste(layer, (int(w / 2 - layer.width / 2), int(center_y - layer.height / 2)))

out = art.copy()
out.paste(Image.new("RGB", (w, h), "white"), (0, 0), clock)
out.paste(art, (0, 0), mask)  # subject in front of the time
date_font = ImageFont.truetype("/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf", int(0.045 * w))
d = ImageDraw.Draw(out)
date = "Sunday 8 November"
dw = d.textlength(date, font=date_font)
d.text((w / 2 - dw / 2, center_y - layer.height / 2 - 0.08 * w), date, font=date_font, fill="white")
out.save(out_path)
