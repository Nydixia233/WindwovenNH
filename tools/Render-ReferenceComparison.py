"""Render synthetic offline comparison frames (requires Pillow; not a mod dependency)."""
from argparse import ArgumentParser
import csv
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


def read_frames(path):
    result = {}
    with path.open(encoding='utf-8', newline='') as stream:
        for r in csv.DictReader(stream):
            key = r['scenario'], int(r['tick']), r['model']
            w, h = int(r['columns']), int(r['rows'])
            if key not in result:
                result[key] = w, h, []
            arr = result[key][2]
            if len(arr) != int(r['row']) * w + int(r['column']):
                raise ValueError(f'Unexpected vertex ordering for {key}')
            arr.append(tuple(float(r[k]) for k in 'xyz'))
    return result


def cross(a, b):
    return a[1]*b[2] - a[2]*b[1], a[2]*b[0] - a[0]*b[2], a[0]*b[1] - a[1]*b[0]


def subtract(a, b):
    return tuple(a[i] - b[i] for i in range(3))


def render_mesh(draw, cx, cy, w, h, nodes, azimuth):
    sa, ca = math.sin(azimuth), math.cos(azimuth)
    ce, se = math.cos(.22), math.sin(.22)

    def transform(p):
        x, y, z = p
        f = sa*x + ca*z
        return cx + (ca*x - sa*z) * 118, cy + (ce*y + se*f) * 118, ce*f - se*y

    polys = []

    def polygon(points, color, border=None):
        t = tuple(transform(p) for p in points)
        polys.append((sum(v[2] for v in t)/len(t), tuple(v[:2] for v in t), color, border))

    def block(x0, x1, y0, y1, z0, z1):
        # Neutral fixture torso, not a Minecraft skin or exported game model.
        polygon([(x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1)], '#63728a', '#46546a')
        polygon([(x1,y0,z0),(x1,y0,z1),(x1,y1,z1),(x1,y1,z0)], '#44516a', '#46546a')
        polygon([(x0,y0,z0),(x1,y0,z0),(x1,y0,z1),(x0,y0,z1)], '#8495ae', '#53637b')

    block(-.24, .24, -.45, -.06, -.37, .025)
    block(-.292, .292, -.03, .66, -.31, 0)
    block(-.224, -.018, .66, 1.31, -.29, -.027)
    block(.018, .224, .66, 1.31, -.29, -.027)
    # The mannequin is a pose guide viewed from behind, not a mesh contact visualization.
    # Sort it separately so a whole torso face can't obscure half of a small drape by centroid.
    for _, points, color, border in sorted(polys, key=lambda p:p[0]):
        draw.polygon(points, fill=color, outline=border)
    polys.clear()
    for r in range(h - 1):
        for c in range(w - 1):
            a = r*w+c
            q = (nodes[a], nodes[a+1], nodes[a+w+1], nodes[a+w])
            n = cross(subtract(q[1], q[0]), subtract(q[3], q[0]))
            strength = math.sqrt(sum(v*v for v in n))
            facing = sum(n[i]*(sa*ce,-se,ca*ce)[i] for i in range(3))
            light = .66 + .32 * abs((n[0]*.31 - n[1]*.59 + n[2]*.75) / max(1e-6, strength))
            base = (227,187,108) if c == 0 or c == w-2 else ((173,95,195) if facing >= 0 else (56,163,170))
            if 3*(w-1)/9 < c+.5 < 5*(w-1)/9 and .38 < (r+.5)/(h-1) < .59:
                base = (227,165,219) if facing >= 0 else (127,210,199)
            rgb = tuple(round(v*light) for v in base)
            polygon(q, rgb)
    for _, points, color, border in sorted(polys, key=lambda p:p[0]):
        draw.polygon(points, fill=color, outline=border)
    draw.line([transform(nodes[i])[:2] for i in range(w)], fill='#f5d087', width=2)


def main():
    parser = ArgumentParser(description=__doc__)
    parser.add_argument('csv', type=Path)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--font', default='C:/Windows/Fonts/msyh.ttc')
    parser.add_argument('--suite', choices=('calibration', 'collar'), default='calibration')
    args = parser.parse_args()
    args.directory.mkdir(parents=True, exist_ok=True)
    scenarios = [('oblique-drop-turn', '斜向下落中转身', range(36,100,2), 74),
                 ('boost-then-brake', '向上加速后急停', range(28,96,2), 64)]
    models = [('old','0.1.4'), ('reference','离线参考'), ('runtime','0.2.0')]
    if args.suite == 'collar':
        scenarios = [('banked-fall', '高速斜落中转身', range(40, 118, 2), 80),
                     ('standing-hop', '原地起跳与落地', range(40, 76, 2), 52)]
        models = [('old','0.2.0'), ('reference','离线参考'), ('runtime','0.2.1')]
    hfont, font, small = (ImageFont.truetype(args.font, s) for s in (23,19,14))
    data = read_frames(args.csv)
    frames, delays, stills = [], [], []
    for case, name, ticks, still in scenarios:
        for tick in ticks:
            image = Image.new('RGB',(912,354),'#172132')
            draw = ImageDraw.Draw(image)
            draw.text((17,9),name,font=hfont,fill='#f1f5fc')
            draw.text((18,42),'同一输入、同一视角 · 这是一组离线布面，不是游戏截图',font=small,fill='#c1cee4')
            for i,(key,label) in enumerate(models):
                w,h,points = data[case,tick,key]
                if w*h != len(points): raise ValueError('Missing cloth vertices')
                x = 15+i*298
                draw.rounded_rectangle((x,76,x+286,327),radius=9,fill='#253248')
                draw.text((x+12,81),label,font=font,fill='#a9e6cf' if key == 'runtime' else '#edf2fa')
                angle = .96 if case in ('boost-then-brake', 'banked-fall') else .61
                render_mesh(draw, x+147, 177, w, h, points, angle)
            draw.text((18,333),f'tick {tick:03d}  ·  参考网格为 17×49；图中仅抽样显示',font=small,fill='#bdcce3')
            if tick == still: stills.append(image.copy())
            frames.append(image.quantize(colors=192,dither=Image.Dither.NONE))
            delays.append(100+ (500 if tick == ticks[-1] else 0))
    out = args.directory
    frames[0].save(out/'reference-comparison.gif',save_all=True,append_images=frames[1:],duration=delays,loop=0,disposal=2,optimize=False)
    cover=Image.new('RGB',(912,708),'#172132')
    for n, image in enumerate(stills): cover.paste(image,(0,n*354))
    cover.save(out/'reference-comparison.png')


if __name__ == '__main__':
    main()