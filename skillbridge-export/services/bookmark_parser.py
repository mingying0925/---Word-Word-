"""解析 .docx 文档中的表格结构与书签信息。"""

from docx import Document
from docx.oxml.ns import qn


def _get_cell_merge_info(tc):
    """读取单元格的合并属性。

    返回 (is_merged, merge_span)：
    - 水平合并 (gridSpan > 1): merge_span 为整数
    - 垂直合并 (vMerge): merge_span 为 "continue" 或 "restart"
    """
    tcPr = tc.find(qn('w:tcPr'))
    if tcPr is None:
        return False, None

    gridSpan = tcPr.find(qn('w:gridSpan'))
    if gridSpan is not None:
        val = gridSpan.get(qn('w:val'))
        try:
            span = int(val) if val is not None else 1
        except (TypeError, ValueError):
            span = 1
        if span > 1:
            return True, span

    vMerge = tcPr.find(qn('w:vMerge'))
    if vMerge is not None:
        val = vMerge.get(qn('w:val'))
        if val == 'restart':
            return True, 'restart'
        return True, 'continue'

    return False, None


def _get_cell_bookmarks(tc):
    """获取单元格内所有书签名称（过滤 Word 自动生成的隐藏书签，如 _GoBack）。"""
    names = []
    for bm in tc.iter(qn('w:bookmarkStart')):
        name = bm.get(qn('w:name'))
        # 过滤以下划线开头的隐藏书签（_GoBack、_topo 等 Word 内部书签）
        if name and not name.startswith('_'):
            names.append(name)
    return names


def _get_cell_text(tc):
    """拼接单元格内所有段落文本。"""
    parts = []
    for p in tc.findall(qn('w:p')):
        text_nodes = [t.text for t in p.iter(qn('w:t')) if t.text]
        parts.append(''.join(text_nodes))
    return ''.join(parts).strip()


def _infer_field_type(name):
    """根据书签名推断控件类型与默认选项。"""
    if '性别' in name:
        return 'radio', ['男', '女']
    if '日期' in name or '时间' in name:
        return 'date', None
    if '照片' in name or 'photo' in name.lower():
        return 'image', None
    return 'text', None


def parse_bookmarks(file_path):
    """解析 .docx 文件,返回所有表格结构与书签汇总信息。"""
    doc = Document(file_path)
    tables_structure = []
    bookmarks_summary = []

    for table_idx, table in enumerate(doc.tables):
        rows = table.rows
        cells_info = []
        max_cols = 0

        for row_idx, row in enumerate(rows):
            col_idx = 0
            for tc in row._tr.findall(qn('w:tc')):
                is_merged, merge_span = _get_cell_merge_info(tc)
                text = _get_cell_text(tc)
                bookmark_names = _get_cell_bookmarks(tc)

                cells_info.append({
                    'row': row_idx,
                    'col': col_idx,
                    'text': text,
                    'is_merged': is_merged,
                    'merge_span': merge_span,
                    'bookmark_names': bookmark_names,
                })

                for bm_name in bookmark_names:
                    field_type, options = _infer_field_type(bm_name)
                    bookmarks_summary.append({
                        'name': bm_name,
                        'table_index': table_idx,
                        'row': row_idx,
                        'col': col_idx,
                        'type': field_type,
                        'options': options,
                    })

                if isinstance(merge_span, int) and merge_span > 1:
                    col_idx += merge_span
                else:
                    col_idx += 1

            if col_idx > max_cols:
                max_cols = col_idx

        tables_structure.append({
            'table_index': table_idx,
            'rows': len(rows),
            'cols': max_cols,
            'cells': cells_info,
        })

    return {
        'bookmarks': bookmarks_summary,
        'tables_structure': tables_structure,
    }
