import os
from pathlib import Path

INPUT_MD = Path("GuideNest-Full-Documentation.md")
OUTPUT_PDF = Path("GuideNest-Full-Documentation.pdf")

PAGE_WIDTH = 595.2
PAGE_HEIGHT = 841.8
MARGIN_LEFT = 50
MARGIN_TOP = 50
MARGIN_BOTTOM = 50
LINE_HEIGHT = 16
MAX_LINE_WIDTH = PAGE_WIDTH - MARGIN_LEFT * 2

FONT_SIZES = {
    1: 20,
    2: 18,
    3: 16,
    4: 14,
    5: 13,
}

FONT_NAME = "/Helvetica"


def escape_pdf_text(text: str) -> str:
    return text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")


def wrap_text(text: str, max_chars: int) -> list[str]:
    if len(text) <= max_chars:
        return [text]
    words = text.split(" ")
    lines = []
    current = ""
    for word in words:
        if current and len(current) + 1 + len(word) > max_chars:
            lines.append(current)
            current = word
        else:
            current = word if not current else current + " " + word
    if current:
        lines.append(current)
    return lines


def parse_markdown(lines: list[str]) -> list[tuple[int, str]]:
    parsed = []
    for line in lines:
        stripped = line.rstrip("\n")
        if not stripped:
            parsed.append((0, ""))
            continue
        if stripped.startswith("###### "):
            parsed.append((6, stripped[7:]))
        elif stripped.startswith("##### "):
            parsed.append((5, stripped[6:]))
        elif stripped.startswith("#### "):
            parsed.append((4, stripped[5:]))
        elif stripped.startswith("### "):
            parsed.append((3, stripped[4:]))
        elif stripped.startswith("## "):
            parsed.append((2, stripped[3:]))
        elif stripped.startswith("# "):
            parsed.append((1, stripped[2:]))
        else:
            parsed.append((0, stripped))
    return parsed


def build_pdf_objects(page_streams: list[bytes]) -> bytes:
    object_datas = []

    def add_obj(data: bytes) -> int:
        object_datas.append(data)
        return len(object_datas)

    # catalog object
    catalog_id = add_obj(b"<< /Type /Catalog /Pages 2 0 R >>\n")

    # placeholder for pages object
    pages_id = add_obj(b"")

    # font object
    font_id = add_obj(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\n")

    page_ids = []
    for content in page_streams:
        content_id = add_obj(b"<< /Length " + str(len(content)).encode("latin1") + b" >>\nstream\n" + content + b"\nendstream\n")
        page_id = add_obj(
            b"<< /Type /Page /Parent " + str(pages_id).encode("latin1") + b" 0 R /MediaBox [0 0 " +
            str(int(PAGE_WIDTH)).encode("latin1") + b" " +
            str(int(PAGE_HEIGHT)).encode("latin1") + b" ] /Resources << /Font << /F1 " +
            str(font_id).encode("latin1") + b" 0 R >> >> /Contents " +
            str(content_id).encode("latin1") + b" 0 R >>\n"
        )
        page_ids.append(page_id)

    kids = b" ".join([f"{page_id} 0 R".encode("latin1") for page_id in page_ids])
    object_datas[pages_id - 1] = b"<< /Type /Pages /Kids [" + kids + b"] /Count " + str(len(page_ids)).encode("latin1") + b" >>\n"

    objects = []
    offsets = []
    current_offset = 0

    for index, data in enumerate(object_datas, start=1):
        offsets.append(current_offset)
        obj = f"{index} 0 obj\n".encode("latin1") + data + b"endobj\n"
        current_offset += len(obj)
        objects.append(obj)

    xref_start = current_offset
    xref = b"xref\n0 " + str(len(objects) + 1).encode("latin1") + b"\n0000000000 65535 f \n"
    for offset in offsets:
        xref += f"{offset:010d} 00000 n \n".encode("latin1")

    trailer = b"trailer<< /Size " + str(len(objects) + 1).encode("latin1") + b" /Root 1 0 R >>\nstartxref\n" + str(xref_start).encode("latin1") + b"\n%%EOF\n"
    return b"%PDF-1.3\n" + b"".join(objects) + xref + trailer


def create_page_stream(parsed: list[tuple[int, str]]) -> bytes:
    lines = []
    for level, text in parsed:
        if level == 1:
            lines.append((FONT_SIZES[1], text.upper()))
            lines.append((FONT_SIZES[0] if 0 in FONT_SIZES else 12, ""))
            continue
        if level == 2:
            lines.append((FONT_SIZES[2], text))
            lines.append((FONT_SIZES[0] if 0 in FONT_SIZES else 12, ""))
            continue
        if level == 3:
            lines.append((FONT_SIZES[3], text))
            lines.append((FONT_SIZES[0] if 0 in FONT_SIZES else 12, ""))
            continue
        if level == 4:
            lines.append((FONT_SIZES[4], text))
            continue
        if level == 5:
            lines.append((FONT_SIZES[5], text))
            continue
        if level == 6:
            lines.append((12, text))
            continue
        if text.startswith("- "):
            wrapped = wrap_text(text[2:], 90)
            for j, w in enumerate(wrapped):
                prefix = "  • " if j == 0 else "    "
                lines.append((12, prefix + w))
            continue
        if text.startswith("* "):
            wrapped = wrap_text(text[2:], 90)
            for j, w in enumerate(wrapped):
                prefix = "  * " if j == 0 else "    "
                lines.append((12, prefix + w))
            continue
        if text.startswith("```"):
            continue
        if text.startswith("| "):
            lines.append((12, text))
            continue
        wrapped = wrap_text(text, 90)
        for w in wrapped:
            lines.append((12, w))
    page_texts = []
    y = PAGE_HEIGHT - MARGIN_TOP
    page_content = [b"q\n/Helvetica 12 Tf\n1 0 0 1 0 0 cm\nBT\n50 %.2f Td\n" % y]
    line_count = 0
    for font_size, text in lines:
        if text == "":
            y -= LINE_HEIGHT
            page_content.append(b"0 -16 Td\n")
            continue
        if y < MARGIN_BOTTOM + LINE_HEIGHT:
            break
        if font_size != 12:
            page_content.append(b"/F1 %d Tf\n" % font_size)
        page_content.append(b"(%s) Tj\n" % escape_pdf_text(text).encode("latin1", "replace"))
        page_content.append(b"0 -16 Td\n")
        y -= LINE_HEIGHT
        if font_size != 12:
            page_content.append(b"/F1 12 Tf\n")
    page_content.append(b"ET\nQ\n")
    return b"".join(page_content)


def split_pages(parsed: list[tuple[int, str]]) -> list[bytes]:
    pages = []
    current = []
    lines_per_page = 40
    count = 0
    for level, text in parsed:
        if count >= lines_per_page and text == "":
            pages.append(create_page_stream(current))
            current = []
            count = 0
        current.append((level, text))
        count += 1
    if current:
        pages.append(create_page_stream(current))
    return pages


def main() -> None:
    if not INPUT_MD.exists():
        raise FileNotFoundError(f"Markdown source not found: {INPUT_MD}")
    with INPUT_MD.open("r", encoding="utf-8") as f:
        source_lines = f.readlines()
    parsed = parse_markdown(source_lines)
    pages = split_pages(parsed)
    pdf_bytes = build_pdf_objects(pages)
    OUTPUT_PDF.write_bytes(pdf_bytes)
    print(f"Created {OUTPUT_PDF} with {len(pages)} page(s)")


if __name__ == "__main__":
    main()
