from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import cm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import Image, Paragraph, Spacer, Table, TableStyle
from reportlab.pdfgen import canvas


ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "deliverables" / "AstroFish-1.0.0"
PDF = OUT / "AstroFish-1.0.0-Quick-Reference-ZH-EN.pdf"
ICON = OUT / "AstroFish-icon-256.png"

pdfmetrics.registerFont(TTFont("HeitiLight", "/System/Library/Fonts/STHeiti Light.ttc", subfontIndex=0))
pdfmetrics.registerFont(TTFont("HeitiMedium", "/System/Library/Fonts/STHeiti Medium.ttc", subfontIndex=0))

W, H = landscape(A4)
c = canvas.Canvas(str(PDF), pagesize=(W, H))
c.setTitle("AstroFish 1.0 中英双语速查卡 / Quick Reference")
c.setAuthor("yxy")

# Background and header
c.setFillColor(colors.HexColor("#F7F9FC")); c.rect(0, 0, W, H, fill=1, stroke=0)
c.setFillColor(colors.HexColor("#08101E")); c.rect(0, H-2.55*cm, W, 2.55*cm, fill=1, stroke=0)
c.drawImage(str(ICON), 0.75*cm, H-2.25*cm, width=1.75*cm, height=1.75*cm, preserveAspectRatio=True, mask="auto")
c.setFont("HeitiMedium", 23); c.setFillColor(colors.white); c.drawString(2.85*cm, H-1.22*cm, "AstroFish 1.0")
c.setFont("HeitiLight", 10); c.setFillColor(colors.HexColor("#C7D3E4")); c.drawString(2.88*cm, H-1.80*cm, "Star View & Long Exposure Intervalometer for Sony α Cameras")
c.setFont("HeitiLight", 8); c.drawRightString(W-0.8*cm, H-1.38*cm, "by yxy · 想飞的咸鱼")
c.drawRightString(W-0.8*cm, H-1.82*cm, "ILCE-7R · PlayMemories Camera Apps")

styles = {
    "h": ParagraphStyle("h", fontName="HeitiMedium", fontSize=11.5, leading=14, textColor=colors.HexColor("#C95322"), spaceAfter=3),
    "p": ParagraphStyle("p", fontName="HeitiLight", fontSize=7.3, leading=9.3, textColor=colors.HexColor("#182B49"), wordWrap="CJK", spaceAfter=2),
    "small": ParagraphStyle("small", fontName="HeitiLight", fontSize=6.65, leading=8.2, textColor=colors.HexColor("#182B49"), wordWrap="CJK"),
    "th": ParagraphStyle("th", fontName="HeitiMedium", fontSize=6.7, leading=8, textColor=colors.white, wordWrap="CJK"),
    "td": ParagraphStyle("td", fontName="HeitiLight", fontSize=6.45, leading=7.7, textColor=colors.HexColor("#182B49"), wordWrap="CJK"),
}

def P(text, kind="p"):
    return Paragraph(text, styles[kind])

def controls(rows, col1=2.3*cm, col2=9.3*cm):
    data = [[P(rows[0][0], "th"), P(rows[0][1], "th")]]
    data += [[P(a, "td"), P(b, "td")] for a,b in rows[1:]]
    t = Table(data, colWidths=[col1,col2], repeatRows=1)
    t.setStyle(TableStyle([
        ("BACKGROUND",(0,0),(-1,0),colors.HexColor("#17365D")),
        ("GRID",(0,0),(-1,-1),0.3,colors.HexColor("#8190A4")),
        ("ROWBACKGROUNDS",(0,1),(-1,-1),[colors.white,colors.HexColor("#EAF0F7")]),
        ("VALIGN",(0,0),(-1,-1),"MIDDLE"),
        ("LEFTPADDING",(0,0),(-1,-1),3),("RIGHTPADDING",(0,0),(-1,-1),3),
        ("TOPPADDING",(0,0),(-1,-1),1.5),("BOTTOMPADDING",(0,0),(-1,-1),1.5),
    ]))
    return t

cn_rows = [
    ("按键", "主实时取景"),
    ("C1", "普通参数 → 普通纯净 → Star View 参数 → Star View 纯净"),
    ("Fn / 上下", "选择快门、光圈、ISO、张数、延迟、间隔；放大时移动区域"),
    ("左右 / 拨轮", "调整当前参数；放大时左右移动区域"),
    ("C2 / AEL", "循环对焦放大倍率；直接转动镜头对焦环"),
    ("半按快门", "退出对焦放大，不执行自动对焦"),
    ("中心 / 全按快门", "开始序列；再次按下请求停止"),
    ("Menu", "进入 31 秒～6 小时定时 BULB／长曝光页面"),
    ("播放键", "打开照片回放；中心键 1×→2×→4×；Fn/DISP 显示直方图"),
    ("删除键", "待机退出；序列中请求停止"),
]
en_rows = [
    ("Control", "Main live view"),
    ("C1", "Normal controls → Normal clean → Star controls → Star clean"),
    ("Fn / Up/Down", "Select shutter, aperture, ISO, shots, delay, or interval; move area when magnified"),
    ("Left/Right / dials", "Adjust the selected setting; move horizontally when magnified"),
    ("C2 / AEL", "Cycle focus magnification; turn the lens focus ring manually"),
    ("Half shutter", "Leave magnification without autofocus"),
    ("Center / full shutter", "Start a sequence; press again to request a stop"),
    ("Menu", "Open timed BULB / long exposure from 31 seconds to 6 hours"),
    ("Playback", "Review photos; Center cycles 1×→2×→4×; Fn/DISP toggles histogram"),
    ("Trash", "Exit while idle; request a stop during a sequence"),
]

cn = [
    P("中文速查", "h"),
    P("<b>安装：</b>PMCA-RE → USB 连接相机 → 选择 <font color='#C95322'>AstroFish-1.0.0.apk</font> → 从相机应用列表启动 AstroFish。应用包名 <font color='#C95322'>com.yxy.astrofish</font>，不会覆盖原 A7R Astro。", "small"),
    Spacer(1,3), controls(cn_rows), Spacer(1,5),
    P("推荐星空流程", "h"),
    P("1. M 档、手动对焦、固定 ISO；关闭长时间曝光降噪。<br/>2. 按 C1 进入 Star View 构图。<br/>3. 按 C2/AEL 放大星点，移动区域并转动对焦环。<br/>4. 半按快门回到全景，拍一张测试照片。<br/>5. 按播放键，Fn/DISP 调出照片直方图后再调参。<br/>6. 超过 30 秒按 Menu 使用定时 BULB；正式拍摄前先测试 3 张。", "small"),
    Spacer(1,3),
    P("<b>直方图：</b>实时直方图用于快速参考；长曝光与 RAW 的最终判断以试拍后的照片回放直方图为准。纯净取景不显示直方图。<br/><b>日志：</b><font color='#C95322'>ASTROFISH/LOG.TXT</font>　<b>咨询：</b>小红书 想飞的咸鱼，ID 616710483", "small"),
]
en = [
    P("English Quick Reference", "h"),
    P("<b>Install:</b> PMCA-RE → connect camera by USB → select <font color='#C95322'>AstroFish-1.0.0.apk</font> → launch AstroFish from the camera app list. Package <font color='#C95322'>com.yxy.astrofish</font> does not replace the original A7R Astro.", "small"),
    Spacer(1,3), controls(en_rows), Spacer(1,5),
    P("Recommended night-sky workflow", "h"),
    P("1. Use M mode, manual focus, fixed ISO, and disable Long Exposure NR.<br/>2. Press C1 to enter Star View and compose.<br/>3. Press C2/AEL, move to a bright star, and turn the focus ring.<br/>4. Half-press the shutter to return to full view and capture a test image.<br/>5. Press Playback, then Fn/DISP for the photo histogram before adjusting exposure.<br/>6. Press Menu for exposures over 30 seconds; test a three-shot sequence first.", "small"),
    Spacer(1,3),
    P("<b>Histogram:</b> the live histogram is a quick guide. For long exposures and RAW, judge the result with the captured-photo histogram. Clean preview hides it.<br/><b>Log:</b> <font color='#C95322'>ASTROFISH/LOG.TXT</font>　<b>Support:</b> Xiaohongshu 想飞的咸鱼, ID 616710483", "small"),
]

content = Table([[cn, en]], colWidths=[13.45*cm, 13.45*cm], hAlign="CENTER")
content.setStyle(TableStyle([
    ("VALIGN",(0,0),(-1,-1),"TOP"),
    ("LEFTPADDING",(0,0),(-1,-1),0.35*cm),("RIGHTPADDING",(0,0),(-1,-1),0.35*cm),
    ("TOPPADDING",(0,0),(-1,-1),0),("BOTTOMPADDING",(0,0),(-1,-1),0),
    ("LINEBEFORE",(1,0),(1,0),0.6,colors.HexColor("#C4CEDB")),
]))
content_width, content_height = content.wrapOn(c, W-1.2*cm, H-3.2*cm)
content.drawOn(c, 0.6*cm, H-2.85*cm-content_height)

c.setFont("HeitiLight", 6.5); c.setFillColor(colors.HexColor("#718096"))
c.drawCentredString(W/2, 0.35*cm, "AstroFish 1.0 · Third-party app for Sony PlayMemories Camera Apps · Not an official Sony application")
c.save()
print(PDF)
