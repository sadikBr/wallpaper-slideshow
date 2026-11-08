"""
Built-in wallpaper scenes for Wallpaper Slideshow, written as SVG.

Every scene returns (art_svg, mask_svg). The mask is the scene's foreground subject in
white on black: it is what may stand in front of the clock (the depth effect). Scenes are
designed for a 1440x3120 (9:19.5) phone screen, with the clock's time digits roughly
between 13% and 31% of the height, so each subject reaches into the lower part of that band.
"""
import math
import random

W, H = 1440, 3120


def svg(body, defs="", background="#000"):
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">'
        f"<defs>{defs}</defs><rect width='{W}' height='{H}' fill='{background}'/>{body}</svg>"
    )


GRAIN = """
<filter id="grain" x="0" y="0" width="100%" height="100%">
  <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="2" seed="7" result="n"/>
  <feColorMatrix type="saturate" values="0"/>
  <feComponentTransfer><feFuncA type="table" tableValues="0 0.07"/></feComponentTransfer>
  <feComposite in2="SourceGraphic" operator="in"/>
</filter>"""


def grain_layer():
    return f"<rect width='{W}' height='{H}' filter='url(#grain)' fill='#fff'/>"


def stars(seed, count, y_max, color="#fff", max_r=2.6):
    rnd = random.Random(seed)
    out = []
    for _ in range(count):
        x = rnd.uniform(0, W)
        y = rnd.uniform(0, y_max) * rnd.uniform(0.3, 1.0)
        r = rnd.uniform(0.6, max_r) * (1.4 if rnd.random() < 0.04 else 1)
        o = rnd.uniform(0.25, 0.95) * (1 - y / y_max * 0.8)
        out.append(f"<circle cx='{x:.1f}' cy='{y:.1f}' r='{r:.2f}' fill='{color}' opacity='{o:.2f}'/>")
    return "".join(out)


def ridge(seed, base_y, amplitude, roughness, peaks=None, step=12):
    """A mountain ridge line as an SVG path closed to the bottom of the screen."""
    rnd = random.Random(seed)
    pts = []
    phase = [rnd.uniform(0, 6.28) for _ in range(4)]
    for x in range(-20, W + 40, step):
        t = x / W
        y = base_y
        y -= amplitude * (0.55 * math.sin(t * 3.1 + phase[0]) + 0.3 * math.sin(t * 7.3 + phase[1]))
        y -= amplitude * roughness * (0.5 * math.sin(t * 19 + phase[2]) + 0.3 * math.sin(t * 41 + phase[3]))
        if peaks:
            for (px, py, width) in peaks:
                # Sharp alpine peak: a narrow tent shape blended in.
                d = abs(x - px) / width
                if d < 1:
                    y = min(y, py + (base_y - py) * (d ** 1.15) + rnd.uniform(-4, 4))
        pts.append((x, y))
    d = f"M-20,{H} " + " ".join(f"L{x:.0f},{y:.0f}" for x, y in pts) + f" L{W + 40},{H} Z"
    return d


def displaced(points, seed, roughness, levels=7):
    """Midpoint displacement: makes a straight polyline look like a natural rocky edge."""
    rnd = random.Random(seed)
    pts = list(points)
    for level in range(levels):
        out = [pts[0]]
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            length = math.hypot(x1 - x0, y1 - y0)
            off = rnd.uniform(-1, 1) * length * roughness
            # Push perpendicular to the segment.
            nx, ny = -(y1 - y0) / (length or 1), (x1 - x0) / (length or 1)
            out.append(((x0 + x1) / 2 + nx * off, (y0 + y1) / 2 + ny * off))
            out.append((x1, y1))
        pts = out
    return pts


def path_of(points, close_to_bottom=True):
    d = "M" + " L".join(f"{x:.1f},{y:.1f}" for x, y in points)
    if close_to_bottom:
        d += f" L{points[-1][0]:.1f},{H} L{points[0][0]:.1f},{H} Z"
    else:
        d += " Z"
    return d


def range_profile(seed, base_y, height, peaks, roughness=0.12, x0=-40, x1=None):
    """A mountain range: a few summits joined by saddles, with a rocky edge."""
    x1 = W + 40 if x1 is None else x1
    rnd = random.Random(seed)
    xs = sorted([x0] + [rnd.uniform(x0, x1) for _ in range(peaks * 2)] + [x1])
    pts = []
    for i, x in enumerate(xs):
        if i in (0, len(xs) - 1):
            y = base_y - height * rnd.uniform(0.1, 0.4)
        elif i % 2:
            y = base_y - height * rnd.uniform(0.6, 1.0)   # summit
        else:
            y = base_y - height * rnd.uniform(0.15, 0.45)  # saddle
        pts.append((x, y))
    return displaced(pts, seed + 1, roughness)


def pine_forest(seed, base_y, count, min_h, max_h, x0=-40, x1=None, tall=None):
    """A row of pine silhouettes. `tall` = (x, top_y) adds one hero tree."""
    x1 = W + 40 if x1 is None else x1
    rnd = random.Random(seed)
    trees = []
    xs = sorted(rnd.uniform(x0, x1) for _ in range(count))
    for x in xs:
        trees.append((x, base_y - rnd.uniform(min_h, max_h) * rnd.uniform(0.75, 1.0), rnd))
    if tall:
        trees.append((tall[0], tall[1], None))
    paths = []
    for x, top, r in trees:
        h = base_y - top
        if r is None:  # The hero tree: slender, with many fine tiers.
            r = random.Random(seed + 99)
            w = h * 0.12
            tiers = max(4, int(h / 38))
        else:
            w = h * r.uniform(0.2, 0.26)
            tiers = max(4, int(h / 55))
        pts = [(x, top)]
        # Branch tiers: irregular widths and drooping tips, so trees don't look stamped.
        right_spread = [w * (t / tiers) ** 0.85 * r.uniform(0.75, 1.15) for t in range(1, tiers + 1)]
        left_spread = [w * (t / tiers) ** 0.85 * r.uniform(0.75, 1.15) for t in range(1, tiers + 1)]
        tier_h = h / tiers
        for t in range(1, tiers + 1):
            y = top + tier_h * t
            spread = right_spread[t - 1]
            pts.append((x + spread * 0.55, y - tier_h * 0.55))
            pts.append((x + spread, y + tier_h * 0.12))  # drooping tip
            pts.append((x + spread * 0.35, y - tier_h * 0.05))
        pts.append((x + w * 0.06, base_y + 40))
        pts.append((x - w * 0.06, base_y + 40))
        for t in range(tiers, 0, -1):
            y = top + tier_h * t
            spread = left_spread[t - 1]
            pts.append((x - spread * 0.35, y - tier_h * 0.05))
            pts.append((x - spread, y + tier_h * 0.12))
            pts.append((x - spread * 0.55, y - tier_h * 0.55))
        paths.append("M" + " L".join(f"{px:.1f},{py:.1f}" for px, py in pts) + " Z")
    # Ground under the trees.
    paths.append(f"M-40,{base_y + 20} L{W + 40},{base_y + 20} L{W + 40},{H} L-40,{H} Z")
    return " ".join(paths)


def violet_peaks():
    defs = GRAIN + """
<linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="#140F38"/>
  <stop offset="0.25" stop-color="#33268F"/>
  <stop offset="0.45" stop-color="#6E3AD6"/>
  <stop offset="0.6" stop-color="#C45CB5"/>
  <stop offset="0.72" stop-color="#FF9A8A"/>
  <stop offset="0.8" stop-color="#FFC9A0"/>
</linearGradient>
<radialGradient id="sun" cx="0.5" cy="0.5" r="0.5">
  <stop offset="0" stop-color="#FFF6E2"/>
  <stop offset="0.12" stop-color="#FFE2B8"/>
  <stop offset="0.35" stop-color="#FFB38F" stop-opacity="0.55"/>
  <stop offset="1" stop-color="#FF8A9B" stop-opacity="0"/>
</radialGradient>
<linearGradient id="r1" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="#D98BC4"/><stop offset="1" stop-color="#A56BC9"/>
</linearGradient>
<linearGradient id="r2" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="#9A5FC2"/><stop offset="1" stop-color="#6A45AE"/>
</linearGradient>
<linearGradient id="r3" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="#5E3BA0"/><stop offset="1" stop-color="#36257A"/>
</linearGradient>
<linearGradient id="lit" x1="0" y1="0" x2="0.3" y2="1">
  <stop offset="0" stop-color="#E7A6D6"/><stop offset="0.25" stop-color="#A76FD3"/><stop offset="0.7" stop-color="#5A3AA4"/><stop offset="1" stop-color="#2E1E70"/>
</linearGradient>
<linearGradient id="shade" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="#2A1C66"/><stop offset="1" stop-color="#140D3A"/>
</linearGradient>
<linearGradient id="snow" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="#FFF0F6"/><stop offset="1" stop-color="#E6C4F4" stop-opacity="0"/>
</linearGradient>
<linearGradient id="mist" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="#FFB7A6" stop-opacity="0"/>
  <stop offset="0.6" stop-color="#F3A3C0" stop-opacity="0.5"/>
  <stop offset="1" stop-color="#F3A3C0" stop-opacity="0"/>
</linearGradient>
<linearGradient id="forest" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="#1C1248"/><stop offset="1" stop-color="#0B0722"/>
</linearGradient>
"""
    summit = (930, 610)
    left_base, right_base = (40, 3000), (1560, 2150)
    left = displaced([left_base, (330, 2250), (520, 1750), (640, 1560), (780, 1080), summit], 21, 0.07)
    right = displaced([summit, (1060, 880), (1150, 1180), (1290, 1420), right_base], 22, 0.07)
    outline = left + right[1:]
    hero = path_of(outline)
    # Sunlit face (the sun is low on the left): left of a ridge line running down from the summit.
    ridge_line = displaced([summit, (960, 1050), (1000, 1650), (1060, 3100)], 23, 0.05)
    lit = path_of(left + ridge_line[1:], close_to_bottom=False)
    # A few rock gullies on the lit face for texture.
    gullies = " ".join(
        path_of(displaced([(x, y), (x + dx, y + 520)], 60 + i, 0.08) + displaced([(x + dx + 26, y + 520), (x + 14, y)], 70 + i, 0.08), close_to_bottom=False)
        for i, (x, y, dx) in enumerate([(840, 1050, -120), (760, 1500, -170), (900, 1350, -60), (640, 1900, -200)])
    )
    snow_cap = path_of(
        displaced([(800, 930), summit], 24, 0.04) + displaced([summit, (1080, 900)], 25, 0.04)
        + displaced([(1080, 900), (1010, 980), (960, 900), (920, 1010), (870, 940), (800, 930)], 26, 0.05),
        close_to_bottom=False,
    )
    far = path_of(range_profile(31, 1850, 330, 4, 0.16))
    mid = path_of(range_profile(32, 2200, 420, 3, 0.16))
    forest = pine_forest(41, 2930, 80, 150, 420)
    body = (
        "<rect width='1440' height='3120' fill='url(#sky)'/>"
        + stars(11, 320, 1300)
        + "<circle cx='430' cy='1640' r='620' fill='url(#sun)'/>"
        + f"<path d='{far}' fill='url(#r1)'/>"
        + "<rect y='1650' width='1440' height='420' fill='url(#mist)'/>"
        + f"<path d='{mid}' fill='url(#r2)'/>"
        + "<rect y='2000' width='1440' height='380' fill='url(#mist)' opacity='0.8'/>"
        + f"<path d='{hero}' fill='url(#shade)'/>"
        + f"<path d='{lit}' fill='url(#lit)'/>"
        + f"<path d='{gullies}' fill='#2A1C66' opacity='0.18'/>"
        + f"<path d='{snow_cap}' fill='url(#snow)' opacity='0.92'/>"
        + f"<path d='{forest}' fill='url(#forest)'/>"
        + grain_layer()
    )
    mask = f"<path d='{hero}' fill='#fff'/><path d='{forest}' fill='#fff'/>"
    return svg(body, defs), svg(mask)


def northern_lights():
    defs = GRAIN + """
<linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="#030B17"/>
  <stop offset="0.35" stop-color="#08203A"/>
  <stop offset="0.6" stop-color="#0E3A4F"/>
  <stop offset="0.75" stop-color="#1E5560"/>
</linearGradient>
<linearGradient id="aur1" x1="0" y1="0" x2="1" y2="0">
  <stop offset="0" stop-color="#2BF0A8" stop-opacity="0"/>
  <stop offset="0.25" stop-color="#2BF0A8"/>
  <stop offset="0.6" stop-color="#39D4F5"/>
  <stop offset="0.9" stop-color="#9A6CF5"/>
  <stop offset="1" stop-color="#9A6CF5" stop-opacity="0"/>
</linearGradient>
<linearGradient id="aur2" x1="1" y1="0" x2="0" y2="0">
  <stop offset="0" stop-color="#52F5C0" stop-opacity="0"/>
  <stop offset="0.3" stop-color="#52F5C0"/>
  <stop offset="0.75" stop-color="#C77BF2"/>
  <stop offset="1" stop-color="#C77BF2" stop-opacity="0"/>
</linearGradient>
<filter id="glow" x="-20%" y="-50%" width="140%" height="200%"><feGaussianBlur stdDeviation="38"/></filter>
<filter id="rays" x="-10%" y="-30%" width="120%" height="160%">
  <feTurbulence type="fractalNoise" baseFrequency="0.012 0.0008" numOctaves="2" seed="3" result="t"/>
  <feColorMatrix in="t" type="luminanceToAlpha" result="a"/>
  <feComposite in="SourceGraphic" in2="a" operator="in" result="streaks"/>
  <feGaussianBlur in="streaks" stdDeviation="10"/>
</filter>
<linearGradient id="snowpk" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="#DDF6FF"/><stop offset="0.35" stop-color="#7FB7C9"/><stop offset="1" stop-color="#1B4552"/>
</linearGradient>
<linearGradient id="far" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="#2D6B78"/><stop offset="1" stop-color="#173E4B"/>
</linearGradient>
<linearGradient id="lake" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="#1B5A62"/><stop offset="1" stop-color="#061722"/>
</linearGradient>
<linearGradient id="forest" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="#03111A"/><stop offset="1" stop-color="#010609"/>
</linearGradient>
"""
    aurora = (
        "<g filter='url(#glow)' opacity='0.9'>"
        "<path d='M-100,1150 C250,700 520,1200 820,820 S1250,520 1560,760' stroke='url(#aur1)' stroke-width='170' fill='none'/>"
        "<path d='M-100,620 C300,420 600,820 900,560 S1300,300 1560,460' stroke='url(#aur2)' stroke-width='120' fill='none' opacity='0.7'/>"
        "</g>"
        "<g filter='url(#rays)' opacity='0.85'>"
        "<path d='M-100,1150 C250,700 520,1200 820,820 S1250,520 1560,760 L1560,260 S1250,20 820,320 S250,200 -100,650 Z' fill='url(#aur1)'/>"
        "</g>"
    )
    peaks = path_of(range_profile(51, 2250, 560, 4, 0.18))
    far = path_of(range_profile(52, 2300, 260, 5, 0.14))
    forest = pine_forest(53, 2620, 60, 260, 520, tall=(1000, 640))
    reflection = f"<g transform='translate(0,{2 * 2380}) scale(1,-1)' opacity='0.35'><path d='{peaks}' fill='url(#snowpk)'/></g>"
    body = (
        "<rect width='1440' height='3120' fill='url(#sky)'/>"
        + stars(61, 420, 1700, max_r=2.2)
        + aurora
        + f"<path d='{peaks}' fill='url(#snowpk)'/>"
        + f"<path d='{far}' fill='url(#far)'/>"
        + "<rect y='2380' width='1440' height='740' fill='url(#lake)'/>"
        + f"<clipPath id='lakeclip'><rect y='2380' width='1440' height='740'/></clipPath><g clip-path='url(#lakeclip)'>{reflection}</g>"
        + f"<path d='{forest}' fill='url(#forest)'/>"
        + grain_layer()
    )
    return svg(body, defs), svg(f"<path d='{forest}' fill='#fff'/>")


def palm_frond(cx, cy, direction, length, lift, droop, seed):
    """
    One palm frond: an arched rib (up and out, then drooping) with thin leaflets hanging
    from it, as a list of SVG stroke paths.
    """
    rnd = random.Random(seed)
    p0 = (cx, cy)
    p1 = (cx + direction * length * 0.5, cy - length * lift)
    p2 = (cx + direction * length, cy + length * droop)

    def at(t):
        a, b, c = (1 - t) ** 2, 2 * (1 - t) * t, t * t
        return (a * p0[0] + b * p1[0] + c * p2[0], a * p0[1] + b * p1[1] + c * p2[1])

    rib = "M{:.0f},{:.0f} Q{:.0f},{:.0f} {:.0f},{:.0f}".format(*p0, *p1, *p2)
    leaflets = []
    steps = 34
    for i in range(2, steps):
        t = i / steps
        x, y = at(t)
        nx, ny = at(min(1, t + 0.01))
        tx, ty = nx - x, ny - y
        n = math.hypot(tx, ty) or 1
        tx, ty = tx / n, ty / n
        size = length * 0.3 * math.sin(math.pi * t) ** 0.7 * rnd.uniform(0.85, 1.1)
        for side in (-1, 1):
            # Leaflets point back along the rib and hang down with gravity.
            ang = math.atan2(ty, tx) + side * math.radians(rnd.uniform(48, 62))
            lx = x + math.cos(ang) * size * 0.55 - tx * size * 0.15
            ly = y + math.sin(ang) * size * 0.55 + size * 0.45
            mx = x + math.cos(ang) * size * 0.3
            my = y + math.sin(ang) * size * 0.3 + size * 0.08
            leaflets.append(f"M{x:.0f},{y:.0f} Q{mx:.0f},{my:.0f} {lx:.0f},{ly:.0f}")
    return rib, leaflets


def golden_palm():
    defs = GRAIN + """
<linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="#22174F"/>
  <stop offset="0.3" stop-color="#7B3FA8"/>
  <stop offset="0.5" stop-color="#E0587A"/>
  <stop offset="0.62" stop-color="#FF9A5C"/>
  <stop offset="0.66" stop-color="#FFD08A"/>
</linearGradient>
<radialGradient id="sun" cx="0.5" cy="0.5" r="0.5">
  <stop offset="0" stop-color="#FFF4D2"/><stop offset="0.45" stop-color="#FFD58A"/>
  <stop offset="0.5" stop-color="#FFB86B" stop-opacity="0.6"/><stop offset="1" stop-color="#FF8A5C" stop-opacity="0"/>
</radialGradient>
<linearGradient id="sea" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="#F08A7A"/><stop offset="0.3" stop-color="#9A4E8E"/><stop offset="1" stop-color="#2A1A55"/>
</linearGradient>
<linearGradient id="palm" x1="0" y1="400" x2="0" y2="3120" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#2E1440"/><stop offset="1" stop-color="#120820"/>
</linearGradient>
<linearGradient id="glint" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="#FFE3A8" stop-opacity="0.9"/><stop offset="1" stop-color="#FFE3A8" stop-opacity="0"/>
</linearGradient>
<filter id="blur8"><feGaussianBlur stdDeviation="3"/></filter>
"""
    horizon = 2000
    rnd = random.Random(81)
    glints = "".join(
        f"<rect x='{560 + rnd.gauss(0, 60) - w / 2:.0f}' y='{y:.0f}' width='{w:.0f}' height='{rnd.uniform(5, 10):.0f}' rx='4' fill='#FFE3A8' opacity='{0.9 - (y - horizon) / 1300:.2f}'/>"
        for y, w in ((horizon + 14 + i * i * 1.6, rnd.uniform(60, 260) * (1 + i / 20)) for i in range(32))
    )
    # Trunk: a gentle curve from bottom right up to the crown.
    crown = (840, 860)
    trunk = (f"M1180,3140 C1150,2500 1020,1600 {crown[0] + 24},{crown[1] + 30} "
             f"L{crown[0] - 16},{crown[1] + 24} C990,1600 1100,2500 1080,3140 Z")
    ribs, leaves = [], []
    for i, (direction, length, lift, droop) in enumerate([
        (-1, 640, 0.42, 0.55), (-1, 560, 0.62, 0.15), (-1, 420, 0.75, -0.2),
        (1, 660, 0.40, 0.6), (1, 560, 0.6, 0.18), (1, 430, 0.78, -0.18),
        (-0.25, 360, 0.95, -0.35), (0.3, 380, 0.92, -0.3),
    ]):
        rib, leaflets = palm_frond(crown[0], crown[1], direction, length, lift, droop, 90 + i)
        ribs.append(rib)
        leaves.extend(leaflets)
    fronds_svg = (
        f"<path d='{' '.join(leaves)}' stroke='url(#palm)' stroke-width='11' stroke-linecap='round' fill='none'/>"
        f"<path d='{' '.join(ribs)}' stroke='url(#palm)' stroke-width='18' stroke-linecap='round' fill='none'/>"
    )
    fronds_mask = (
        f"<path d='{' '.join(leaves)}' stroke='#fff' stroke-width='11' stroke-linecap='round' fill='none'/>"
        f"<path d='{' '.join(ribs)}' stroke='#fff' stroke-width='18' stroke-linecap='round' fill='none'/>"
    )
    coconuts = "".join(f"<circle cx='{crown[0] + dx}' cy='{crown[1] + dy}' r='26' fill='#1A0B26'/>" for dx, dy in ((-20, 40), (18, 46), (0, 70)))
    dunes = "M-40,2900 C300,2780 700,2860 1000,2800 S1400,2760 1480,2790 L1480,3140 L-40,3140 Z"
    body = (
        "<rect width='1440' height='3120' fill='url(#sky)'/>"
        + stars(82, 120, 900, max_r=1.8)
        + f"<circle cx='560' cy='{horizon}' r='420' fill='url(#sun)'/>"
        + f"<rect y='{horizon}' width='1440' height='{3120 - horizon}' fill='url(#sea)'/>"
        + f"<g filter='url(#blur8)'>{glints}</g>"
        + f"<path d='{dunes}' fill='#1A0C28'/>"
        + f"<path d='{trunk}' fill='url(#palm)'/>"
        + fronds_svg + coconuts
        + grain_layer()
    )
    mask = f"<path d='{trunk}' fill='#fff'/>{fronds_mask}<path d='{dunes}' fill='#fff'/>"
    return svg(body, defs), svg(mask)


def blue_hour():
    defs = GRAIN + """
<linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="#0A1A3F"/>
  <stop offset="0.4" stop-color="#1F4C8F"/>
  <stop offset="0.6" stop-color="#5D86C2"/>
  <stop offset="0.7" stop-color="#E7A38C"/>
  <stop offset="0.74" stop-color="#F6C59A"/>
</linearGradient>
<linearGradient id="sea" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="#4F77AE"/><stop offset="0.5" stop-color="#1F3D6E"/><stop offset="1" stop-color="#0A1631"/>
</linearGradient>
<linearGradient id="cliff" x1="0" y1="0" x2="1" y2="0">
  <stop offset="0" stop-color="#1B2B4F"/><stop offset="0.5" stop-color="#0E1A36"/><stop offset="1" stop-color="#070F22"/>
</linearGradient>
<linearGradient id="tower" x1="0" y1="0" x2="1" y2="0">
  <stop offset="0" stop-color="#F1F4FA"/><stop offset="0.6" stop-color="#C9D3E6"/><stop offset="1" stop-color="#8B9BBB"/>
</linearGradient>
<linearGradient id="beam" x1="1" y1="0" x2="0" y2="0">
  <stop offset="0" stop-color="#FFF2C4" stop-opacity="0.75"/><stop offset="1" stop-color="#FFF2C4" stop-opacity="0"/>
</linearGradient>
<radialGradient id="lamp" cx="0.5" cy="0.5" r="0.5">
  <stop offset="0" stop-color="#FFFBE8"/><stop offset="0.3" stop-color="#FFE7A0" stop-opacity="0.8"/><stop offset="1" stop-color="#FFD27A" stop-opacity="0"/>
</radialGradient>
<filter id="soft"><feGaussianBlur stdDeviation="14"/></filter>
"""
    horizon = 2180
    cliff_pts = displaced([(560, 3140), (640, 2600), (760, 2250), (880, 1960), (1010, 1820), (1180, 1780), (1500, 1760)], 101, 0.06)
    cliff = "M" + " L".join(f"{x:.0f},{y:.0f}" for x, y in cliff_pts) + " L1500,3140 Z"
    tx, top = 1110, 640
    tower = (f"M{tx - 95},1830 L{tx - 70},{top + 260} L{tx + 70},{top + 260} L{tx + 95},1830 Z")
    gallery = f"M{tx - 92},{top + 220} h184 v40 h-184 Z"
    lantern = f"M{tx - 56},{top + 110} h112 v110 h-112 Z"
    roof = f"M{tx - 76},{top + 112} L{tx},{top} L{tx + 76},{top + 112} Z"
    stripes = "".join(
        f"<path d='M{tx - 95 + (1830 - y) * 25 / 1170:.0f},{y} L{tx + 95 - (1830 - y) * 25 / 1170:.0f},{y} L{tx + 95 - (1830 - y + 130) * 25 / 1170:.0f},{y - 130} L{tx - 95 + (1830 - y + 130) * 25 / 1170:.0f},{y - 130} Z' fill='#D1495B'/>"
        for y in (1700, 1440, 1180)
    )
    beam = f"<path d='M{tx},{top + 165} L-100,{top - 260} L-100,{top + 560} Z' fill='url(#beam)' filter='url(#soft)'/>"
    rnd = random.Random(111)
    waves = "".join(
        f"<rect x='{rnd.uniform(-50, 1300):.0f}' y='{y:.0f}' width='{rnd.uniform(40, 200) * (y - horizon + 200) / 600:.0f}' height='{2 + (y - horizon) / 250:.0f}' rx='3' fill='#BFD3F2' opacity='{rnd.uniform(0.15, 0.4):.2f}'/>"
        for y in (horizon + rnd.uniform(10, 1000) ** 1.0 for _ in range(140))
    )
    body = (
        "<rect width='1440' height='3120' fill='url(#sky)'/>"
        + stars(112, 260, 1500, max_r=2)
        + f"<rect y='{horizon}' width='1440' height='{3120 - horizon}' fill='url(#sea)'/>"
        + waves
        + beam
        + f"<path d='{cliff}' fill='url(#cliff)'/>"
        + f"<path d='{tower}' fill='url(#tower)'/>" + stripes
        + f"<path d='{gallery}' fill='#1A2440'/>"
        + f"<path d='{lantern}' fill='#FFE9A8'/>"
        + f"<circle cx='{tx}' cy='{top + 165}' r='150' fill='url(#lamp)'/>"
        + f"<path d='{roof}' fill='#1A2440'/>"
        + grain_layer()
    )
    mask = "".join(f"<path d='{d}' fill='#fff'/>" for d in (cliff, tower, gallery, lantern, roof))
    return svg(body, defs), svg(mask)


def wave_path(base_y, amplitude, wavelength, phase, crest=None):
    pts = []
    for x in range(-40, W + 60, 10):
        y = base_y - amplitude * math.sin(x / wavelength * 2 * math.pi + phase)
        if crest:
            cx, cy, cw = crest
            d = (x - cx) / cw
            y = min(y, y - (base_y - cy) * math.exp(-d * d))
        pts.append((x, y))
    return "M" + " L".join(f"{x:.0f},{y:.0f}" for x, y in pts) + f" L{W + 60},{H} L-40,{H} Z"


def silk():
    defs = GRAIN + """
<linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="#160F3A"/><stop offset="0.45" stop-color="#3B2DB5"/><stop offset="1" stop-color="#7B3FE4"/>
</linearGradient>
<linearGradient id="w1" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#5A44D6"/><stop offset="1" stop-color="#3B2DB5"/></linearGradient>
<linearGradient id="w2" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#8D4FE8"/><stop offset="1" stop-color="#5A35C2"/></linearGradient>
<linearGradient id="w3" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#C45CB5"/><stop offset="1" stop-color="#7B3FE4"/></linearGradient>
<linearGradient id="w4" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FF8A78"/><stop offset="0.6" stop-color="#E0608F"/><stop offset="1" stop-color="#9A47C9"/></linearGradient>
<linearGradient id="w5" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FFC2A0"/><stop offset="0.5" stop-color="#FF7A66"/><stop offset="1" stop-color="#C24C8E"/></linearGradient>
<linearGradient id="sheen" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff" stop-opacity="0.35"/><stop offset="0.08" stop-color="#fff" stop-opacity="0"/></linearGradient>
<filter id="shadow" x="-10%" y="-10%" width="120%" height="130%"><feDropShadow dx="0" dy="-18" stdDeviation="28" flood-color="#0B0626" flood-opacity="0.55"/></filter>
"""
    layers = [
        (wave_path(1500, 120, 1700, 0.4), "w1"),
        (wave_path(1850, 150, 1500, 2.1), "w2"),
        (wave_path(2200, 140, 1300, 4.0), "w3"),
        (wave_path(2500, 130, 1600, 1.0), "w4"),
        (wave_path(2750, 110, 1400, 5.2), "w5"),
    ]
    body = "<rect width='1440' height='3120' fill='url(#bg)'/>" + "".join(
        f"<path d='{d}' fill='url(#{g})' filter='url(#shadow)'/><path d='{d}' fill='url(#sheen)'/>" for d, g in layers
    ) + grain_layer()
    return svg(body, defs), svg("")


def prism():
    defs = GRAIN + """
<filter id="blur" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="160"/></filter>
<linearGradient id="glass" x1="0" y1="0" x2="1" y2="1">
  <stop offset="0" stop-color="#fff" stop-opacity="0.28"/><stop offset="0.5" stop-color="#fff" stop-opacity="0.06"/><stop offset="1" stop-color="#fff" stop-opacity="0.14"/>
</linearGradient>
<linearGradient id="edge" x1="0" y1="0" x2="1" y2="1">
  <stop offset="0" stop-color="#fff" stop-opacity="0.7"/><stop offset="1" stop-color="#fff" stop-opacity="0.1"/>
</linearGradient>
"""
    blobs = (
        "<g filter='url(#blur)'>"
        "<circle cx='300' cy='700' r='520' fill='#3B2DB5'/>"
        "<circle cx='1180' cy='1100' r='560' fill='#7B3FE4'/>"
        "<circle cx='420' cy='2000' r='600' fill='#FF7A66'/>"
        "<circle cx='1150' cy='2600' r='520' fill='#E0608F'/>"
        "<circle cx='720' cy='1500' r='380' fill='#5BC8F5' opacity='0.6'/>"
        "</g>"
    )
    shapes = (
        "<g transform='rotate(-18 720 1700)'>"
        "<rect x='260' y='1180' width='900' height='900' rx='220' fill='url(#glass)' stroke='url(#edge)' stroke-width='4'/>"
        "</g>"
        "<circle cx='1060' cy='2350' r='260' fill='url(#glass)' stroke='url(#edge)' stroke-width='4'/>"
        "<circle cx='330' cy='2650' r='150' fill='url(#glass)' stroke='url(#edge)' stroke-width='3'/>"
    )
    body = "<rect width='1440' height='3120' fill='#17123A'/>" + blobs + shapes + grain_layer()
    return svg(body, defs), svg("")



SCENES = {
    "violet_peaks": ("Violet Peaks", violet_peaks),
    "northern_lights": ("Northern Lights", northern_lights),
    "golden_palm": ("Golden Palm", golden_palm),
    "blue_hour": ("Blue Hour", blue_hour),
    "silk": ("Silk", silk),
    "prism": ("Prism", prism),
}
