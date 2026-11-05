import sys, json
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen
font = TTFont(sys.argv[1]); text = sys.argv[2]; tracking = float(sys.argv[3])  # em
kern_adj = json.loads(sys.argv[4]) if len(sys.argv) > 4 else {}
gs = font.getGlyphSet(); cmap = font.getBestCmap(); upm = font['head'].unitsPerEm
hmtx = font['hmtx']
# GPOS kerning (pair adjustment) lookup via fontTools' simple approach: use kern from GPOS PairPos format 1/2
def gpos_kern(l, r):
    try:
        gpos = font['GPOS'].table
    except KeyError:
        return 0
    for lookup in gpos.LookupList.Lookup:
        if lookup.LookupType not in (2, 9):
            continue
        for st in lookup.SubTable:
            if lookup.LookupType == 9:
                st = st.ExtSubTable
            if st.LookupType != 2: continue
            cov = st.Coverage.glyphs
            if l not in cov: continue
            if st.Format == 1:
                idx = cov.index(l)
                for pvr in st.PairSet[idx].PairValueRecord:
                    if pvr.SecondGlyph == r and pvr.Value1 is not None:
                        return getattr(pvr.Value1, 'XAdvance', 0) or 0
            elif st.Format == 2:
                c1 = st.ClassDef1.classDefs.get(l, 0); c2 = st.ClassDef2.classDefs.get(r, 0)
                rec = st.Class1Record[c1].Class2Record[c2]
                if rec.Value1 is not None:
                    v = getattr(rec.Value1, 'XAdvance', 0) or 0
                    if v: return v
    return 0
x = 0; paths = []; names = [cmap[ord(c)] for c in text]
for i, g in enumerate(names):
    pen = SVGPathPen(gs)
    tpen = TransformPen(pen, (1, 0, 0, -1, x, 0))  # flip y
    gs[g].draw(tpen)
    paths.append(pen.getCommands())
    adv = hmtx[g][0]
    if i < len(names) - 1:
        adv += gpos_kern(g, names[i+1]) + tracking * upm + kern_adj.get(text[i]+text[i+1], 0) * upm
    x += adv
bp = BoundsPen(gs)
# overall bounds
minx=miny=1e9; maxx=maxy=-1e9; x=0
for i, g in enumerate(names):
    b = BoundsPen(gs); gs[g].draw(b)
    if b.bounds:
        x0,y0,x1,y1 = b.bounds
        minx=min(minx,x+x0); maxx=max(maxx,x+x1); miny=min(miny,-y1); maxy=max(maxy,-y0)
    adv = hmtx[g][0]
    if i < len(names)-1: adv += gpos_kern(g, names[i+1]) + tracking*upm + kern_adj.get(text[i]+text[i+1],0)*upm
    x += adv
print(json.dumps({"upm": upm, "d": " ".join(paths), "bounds": [minx, miny, maxx, maxy], "cap": font['OS/2'].sCapHeight}))
