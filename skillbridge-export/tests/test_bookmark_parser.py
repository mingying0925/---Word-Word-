"""测试 bookmark_parser 模块的 parse_bookmarks 函数。"""

import os
import sys

import pytest
from docx.opc.exceptions import PackageNotFoundError

# 将项目根目录加入 sys.path,以便导入 services 模块
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from services.bookmark_parser import parse_bookmarks

# 定位测试用模板文件
BASE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
SAMPLE_TEMPLATE = os.path.join(BASE_DIR, 'uploads', 'sample_template.docx')


def test_parse_existing_docx_returns_correct_structure():
    """解析存在的 docx 文件应返回包含 bookmarks 和 tables_structure 的字典。"""
    result = parse_bookmarks(SAMPLE_TEMPLATE)
    assert isinstance(result, dict)
    assert 'bookmarks' in result
    assert 'tables_structure' in result
    assert isinstance(result['bookmarks'], list)
    assert isinstance(result['tables_structure'], list)
    # 样例模板至少包含一个书签
    assert len(result['bookmarks']) > 0
    # 样例模板至少包含一个表格
    assert len(result['tables_structure']) > 0


def test_bookmark_fields_contain_required_keys():
    """bookmarks 列表中每个元素应包含 name, table_index, row, col, type 字段。"""
    result = parse_bookmarks(SAMPLE_TEMPLATE)
    required_keys = {'name', 'table_index', 'row', 'col', 'type'}
    for bm in result['bookmarks']:
        assert required_keys.issubset(bm.keys()), f"书签缺少必要字段: {bm}"


def test_gender_bookmark_inferred_as_radio():
    """性别类书签应推断为 radio 类型,options 为 ['男', '女']。"""
    result = parse_bookmarks(SAMPLE_TEMPLATE)
    gender_bookmarks = [bm for bm in result['bookmarks'] if '性别' in bm['name']]
    assert len(gender_bookmarks) > 0, "样例模板中未找到性别类书签"
    for bm in gender_bookmarks:
        assert bm['type'] == 'radio'
        assert bm['options'] == ['男', '女']


def test_date_bookmark_inferred_as_date():
    """日期类书签应推断为 date 类型。"""
    result = parse_bookmarks(SAMPLE_TEMPLATE)
    date_bookmarks = [
        bm for bm in result['bookmarks']
        if '日期' in bm['name'] or '时间' in bm['name']
    ]
    assert len(date_bookmarks) > 0, "样例模板中未找到日期类书签"
    for bm in date_bookmarks:
        assert bm['type'] == 'date'
        assert bm['options'] is None


def test_photo_bookmark_inferred_as_image():
    """照片类书签应推断为 image 类型。"""
    result = parse_bookmarks(SAMPLE_TEMPLATE)
    photo_bookmarks = [
        bm for bm in result['bookmarks']
        if '照片' in bm['name'] or 'photo' in bm['name'].lower()
    ]
    assert len(photo_bookmarks) > 0, "样例模板中未找到照片类书签"
    for bm in photo_bookmarks:
        assert bm['type'] == 'image'
        assert bm['options'] is None


def test_text_bookmark_inferred_as_text():
    """普通文本书签应推断为 text 类型。"""
    result = parse_bookmarks(SAMPLE_TEMPLATE)
    name_bookmarks = [bm for bm in result['bookmarks'] if bm['name'] == '姓名']
    assert len(name_bookmarks) > 0, "样例模板中未找到姓名书签"
    for bm in name_bookmarks:
        assert bm['type'] == 'text'
        assert bm['options'] is None


def test_parse_nonexistent_file_raises_exception():
    """文件不存在时应抛出异常。"""
    with pytest.raises(PackageNotFoundError):
        parse_bookmarks('nonexistent_file.docx')


def test_tables_structure_contains_required_fields():
    """tables_structure 中每个 table 应包含 table_index, rows, cols, cells 字段。"""
    result = parse_bookmarks(SAMPLE_TEMPLATE)
    required_keys = {'table_index', 'rows', 'cols', 'cells'}
    for table in result['tables_structure']:
        assert required_keys.issubset(table.keys()), f"表格结构缺少必要字段: {table}"
        assert isinstance(table['table_index'], int)
        assert isinstance(table['rows'], int)
        assert isinstance(table['cols'], int)
        assert isinstance(table['cells'], list)
