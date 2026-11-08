"""
Renders the built-in wallpapers into the app's assets.

    python3 tools/wallpapers/build.py

Needs Node with Playwright (Chromium), Pillow and ffmpeg. Writes, for each scene,
app/src/main/assets/wallpapers/<id>.webp and, when the scene has a subject,
<id>_mask.png (white = subject in front of the clock). Also renders the animated
"Prism Flow" loop as <id>.mp4.
"""
import os
import subprocess
import sys
import tempfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import scenes  # noqa: E402

OUT = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "wallpapers")


def render(svg_text, width, height, tmp, name):
    svg_path = os.path.join(tmp, name + ".svg")
    png_path = os.path.join(tmp, name + ".png")
    with open(svg_path, "w") as f:
        f.write(svg_text)
    subprocess.run(["node", os.path.join(HERE, "render.js"), svg_path, png_path, str(width), str(height)], check=True)
    return png_path


def build_images(tmp):
    for scene_id, (name, make) in scenes.SCENES.items():
        art, mask = make()
        art_png = render(art, scenes.W, scenes.H, tmp, scene_id)
        Image.open(art_png).convert("RGB").save(os.path.join(OUT, scene_id + ".webp"), "WEBP", quality=88, method=6)
        if "<path" in mask:
            mask_png = render(mask, scenes.W // 2, scenes.H // 2, tmp, scene_id + "_mask")
            Image.open(mask_png).convert("L").save(os.path.join(OUT, scene_id + "_mask.png"), optimize=True)
        print("built", scene_id, "-", name)


def build_flow(tmp, frames=360, fps=30):
    frame_dir = os.path.join(tmp, "flow")
    os.makedirs(frame_dir, exist_ok=True)
    script = f"""
const {{ chromium }} = require('playwright');
(async () => {{
  const browser = await chromium.launch();
  const page = await browser.newPage({{ viewport: {{ width: 540, height: 1170 }} }});
  await page.goto('file://{os.path.join(HERE, "flow.html")}');
  for (let i = 0; i < {frames}; i++) {{
    await page.evaluate((t) => window.render(t), i / {frames});
    await page.screenshot({{ path: '{frame_dir}/' + String(i).padStart(4, '0') + '.png' }});
  }}
  await browser.close();
}})();
"""
    js = os.path.join(tmp, "flow.js")
    with open(js, "w") as f:
        f.write(script)
    subprocess.run(["node", js], check=True)
    subprocess.run([
        "ffmpeg", "-y", "-loglevel", "error", "-framerate", str(fps), "-i", os.path.join(frame_dir, "%04d.png"),
        "-vf", "scale=720:1560:flags=lanczos", "-c:v", "libx264", "-profile:v", "high", "-crf", "24",
        "-preset", "slow", "-pix_fmt", "yuv420p", "-movflags", "+faststart", "-an",
        os.path.join(OUT, "prism_flow.mp4"),
    ], check=True)
    # A still frame for the gallery thumbnail.
    preview = os.path.join(tmp, "prism_flow_preview.png")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", os.path.join(OUT, "prism_flow.mp4"),
                    "-frames:v", "1", preview], check=True)
    Image.open(preview).convert("RGB").save(os.path.join(OUT, "prism_flow_preview.webp"), "WEBP", quality=85)
    print("built prism_flow - Prism Flow")


def write_vmask(path, mask_png, fps, frame_count, size=256):
    """Writes a VideoMaskSequence file (see VideoMaskSequence.kt) with the same cut-out in every frame."""
    import struct
    import zlib
    mask = Image.open(mask_png).convert("L")
    scale = size / max(mask.size)
    mask = mask.resize((max(1, int(mask.width * scale)), max(1, int(mask.height * scale))), Image.BILINEAR)
    data = zlib.compress(mask.tobytes(), 6)
    with open(path, "wb") as f:
        f.write(struct.pack(">iifiii", 0x564D534B, 1, fps, mask.width, mask.height, frame_count))
        for _ in range(frame_count):
            f.write(struct.pack(">i", len(data)))
            f.write(data)


def build_aurora_live(tmp, seconds=8, fps=24):
    """Northern Lights with a moving aurora: a video whose subject (the forest) stays in front of the clock."""
    frame_dir = os.path.join(tmp, "aurora")
    os.makedirs(frame_dir, exist_ok=True)
    frames = seconds * fps
    svgs = []
    for i in range(frames):
        art, _ = scenes.northern_lights(phase=i / frames)
        path = os.path.join(frame_dir, f"{i:04d}.svg")
        with open(path, "w") as f:
            f.write(art)
        svgs.append(path)
    script = f"""
const {{ chromium }} = require('playwright');
const fs = require('fs');
(async () => {{
  const browser = await chromium.launch();
  const page = await browser.newPage({{ viewport: {{ width: 540, height: 1170 }} }});
  for (let i = 0; i < {frames}; i++) {{
    const name = String(i).padStart(4, '0');
    const svg = fs.readFileSync('{frame_dir}/' + name + '.svg', 'utf8').replace(/width="\\d+" height="\\d+"/, 'width="540" height="1170"');
    await page.setContent('<html><body style="margin:0">' + svg + '</body></html>');
    await page.screenshot({{ path: '{frame_dir}/' + name + '.png' }});
  }}
  await browser.close();
}})();
"""
    js = os.path.join(tmp, "aurora.js")
    with open(js, "w") as f:
        f.write(script)
    subprocess.run(["node", js], check=True)
    subprocess.run([
        "ffmpeg", "-y", "-loglevel", "error", "-framerate", str(fps), "-i", os.path.join(frame_dir, "%04d.png"),
        "-vf", "scale=720:1560:flags=lanczos", "-c:v", "libx264", "-profile:v", "high", "-crf", "23",
        "-preset", "slow", "-pix_fmt", "yuv420p", "-movflags", "+faststart", "-an",
        os.path.join(OUT, "aurora_live.mp4"),
    ], check=True)
    _, mask = scenes.northern_lights()
    mask_png = render(mask, scenes.W // 2, scenes.H // 2, tmp, "aurora_live_mask")
    write_vmask(os.path.join(OUT, "aurora_live.vmask"), mask_png, fps=8.0, frame_count=seconds * 8)
    preview = os.path.join(frame_dir, "0000.png")
    Image.open(preview).convert("RGB").save(os.path.join(OUT, "aurora_live_preview.webp"), "WEBP", quality=85)
    print("built aurora_live - Aurora Live")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        build_images(tmp)
        build_flow(tmp)
        build_aurora_live(tmp)
