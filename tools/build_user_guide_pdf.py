from pathlib import Path
import html
import re

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate, Frame, Image, PageBreak, PageTemplate, Paragraph,
    Spacer, Table, TableStyle,
)


ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "deliverables" / "AstroFish-1.0.0"
MD = OUT / "AstroFish-1.0.0-User-Guide-ZH-EN.md"
PNG = OUT / "AstroFish-1.0.0-Interface.png"
PDF = OUT / "AstroFish-1.0.0-User-Guide-ZH-EN.pdf"

pdfmetrics.registerFont(TTFont("HeitiLight", "/System/Library/Fonts/STHeiti Light.ttc", subfontIndex=0))
pdfmetrics.registerFont(TTFont("HeitiMedium", "/System/Library/Fonts/STHeiti Medium.ttc", subfontIndex=0))
pdfmetrics.registerFontFamily("Heiti", normal="HeitiLight", bold="HeitiMedium")


class GuideDoc(BaseDocTemplate):
    def __init__(self, filename):
        super().__init__(filename, pagesize=A4, leftMargin=1.55*cm, rightMargin=1.55*cm,
                         topMargin=1.55*cm, bottomMargin=1.45*cm,
                         title="AstroFish 1.0 使用说明 / User Guide", author="yxy")
        frame = Frame(self.leftMargin, self.bottomMargin, self.width, self.height, id="main")
        self.addPageTemplates(PageTemplate(id="guide", frames=frame, onPage=self.decorate))

    def decorate(self, canvas, doc):
        canvas.saveState()
        canvas.setFont("HeitiLight", 7.5)
        canvas.setFillColor(colors.HexColor("#68768A"))
        canvas.drawRightString(A4[0]-1.55*cm, A4[1]-0.85*cm, "AstroFish 1.0  ·  Sony α Camera App  ·  yxy")
        canvas.drawCentredString(A4[0]/2, 0.72*cm, f"AstroFish 1.0 User Guide  |  {doc.page}")
        canvas.restoreState()


def inline(text):
    parts = re.split(r"(\*\*.*?\*\*|`.*?`)", text)
    out = []
    for part in parts:
        if part.startswith("**") and part.endswith("**"):
            out.append("<b>" + html.escape(part[2:-2]) + "</b>")
        elif part.startswith("`") and part.endswith("`"):
            out.append('<font color="#C95322">' + html.escape(part[1:-1]) + "</font>")
        else:
            out.append(html.escape(part))
    return "".join(out).replace("  ", " ")


def make_styles():
    base = dict(fontName="HeitiLight", textColor=colors.HexColor("#172B4D"), wordWrap="CJK")
    return {
        "cover": ParagraphStyle("cover", fontName="HeitiMedium", fontSize=28, leading=34, alignment=TA_CENTER, textColor=colors.HexColor("#132B4B"), spaceAfter=16, wordWrap="CJK"),
        "meta": ParagraphStyle("meta", **base, fontSize=10.5, leading=17, alignment=TA_CENTER, spaceAfter=5),
        "h1": ParagraphStyle("h1", fontName="HeitiMedium", fontSize=18, leading=23, textColor=colors.HexColor("#132B4B"), spaceBefore=2, spaceAfter=10, wordWrap="CJK"),
        "h2": ParagraphStyle("h2", fontName="HeitiMedium", fontSize=12.1, leading=15, textColor=colors.HexColor("#C95322"), spaceBefore=5.5, spaceAfter=3, keepWithNext=True, wordWrap="CJK"),
        "h3": ParagraphStyle("h3", fontName="HeitiMedium", fontSize=10.3, leading=13, textColor=colors.HexColor("#132B4B"), spaceBefore=5, spaceAfter=2, keepWithNext=True, wordWrap="CJK"),
        "body": ParagraphStyle("body", **base, fontSize=8.45, leading=11.55, spaceAfter=3.1),
        "bullet": ParagraphStyle("bullet", **base, fontSize=8.35, leading=11.05, leftIndent=12, firstLineIndent=-8, bulletIndent=2, spaceAfter=1.1),
        "number": ParagraphStyle("number", **base, fontSize=8.35, leading=11.05, leftIndent=14, firstLineIndent=-11, spaceAfter=1.1),
        "table": ParagraphStyle("table", fontName="HeitiLight", fontSize=7.6, leading=9.4, textColor=colors.HexColor("#172B4D"), wordWrap="CJK"),
        "table_head": ParagraphStyle("table_head", fontName="HeitiMedium", fontSize=7.6, leading=9.4, textColor=colors.white, wordWrap="CJK"),
    }


def build():
    st = make_styles()
    story = []
    lines = MD.read_text(encoding="utf-8").splitlines()
    i = 0
    while i < len(lines):
        line = lines[i].rstrip()
        if not line:
            i += 1; continue
        if line == "---":
            story.append(PageBreak()); i += 1; continue
        if line.startswith("!["):
            img = Image(str(PNG), width=17.0*cm, height=9.633*cm)
            story.extend([Spacer(1, 3), img, Spacer(1, 5)])
            i += 1; continue
        if line.startswith("|") and i + 1 < len(lines) and re.match(r"^\|[-: |]+\|$", lines[i+1]):
            raw = [[c.strip() for c in line.strip("|").split("|")]]
            i += 2
            while i < len(lines) and lines[i].startswith("|"):
                raw.append([c.strip() for c in lines[i].strip("|").split("|")]); i += 1
            data = []
            for r, row in enumerate(raw):
                data.append([Paragraph(inline(c), st["table_head" if r == 0 else "table"]) for c in row])
            widths = [4.4*cm, 12.5*cm] if len(raw[0]) == 2 else None
            table = Table(data, colWidths=widths, repeatRows=1, hAlign="LEFT")
            table.setStyle(TableStyle([
                ("BACKGROUND", (0,0), (-1,0), colors.HexColor("#17365D")),
                ("GRID", (0,0), (-1,-1), 0.35, colors.HexColor("#738092")),
                ("VALIGN", (0,0), (-1,-1), "MIDDLE"),
                ("LEFTPADDING", (0,0), (-1,-1), 4), ("RIGHTPADDING", (0,0), (-1,-1), 4),
                ("TOPPADDING", (0,0), (-1,-1), 2.3), ("BOTTOMPADDING", (0,0), (-1,-1), 2.3),
                ("ROWBACKGROUNDS", (0,1), (-1,-1), [colors.white, colors.HexColor("#EEF3F8")]),
            ]))
            story.extend([table, Spacer(1, 4)])
            continue
        if line.startswith("# "):
            is_cover = i == 0
            story.append(Paragraph(inline(line[2:]), st["cover" if is_cover else "h1"]))
            if is_cover: story.append(Spacer(1, 14))
            i += 1; continue
        if line.startswith("## "):
            story.append(Paragraph(inline(line[3:]), st["h2"])); i += 1; continue
        if line.startswith("### "):
            story.append(Paragraph(inline(line[4:]), st["h3"])); i += 1; continue
        m = re.match(r"^- (.*)$", line)
        if m:
            story.append(Paragraph("• " + inline(m.group(1)), st["bullet"])); i += 1; continue
        m = re.match(r"^(\d+)\. (.*)$", line)
        if m:
            story.append(Paragraph(f'<font name="HeitiMedium" color="#C95322">{m.group(1)}.</font> ' + inline(m.group(2)), st["number"])); i += 1; continue
        story.append(Paragraph(inline(line), st["meta" if i < 6 else "body"])); i += 1
    GuideDoc(str(PDF)).build(story)
    print(PDF)


if __name__ == "__main__":
    build()
