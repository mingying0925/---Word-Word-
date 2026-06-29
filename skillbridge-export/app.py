import json
import os
import uuid

from flask import Flask, after_this_request, jsonify, request, send_file
from flask_cors import CORS
from werkzeug.utils import secure_filename

from services.bookmark_parser import parse_bookmarks
from services.docx_filler import fill_docx


BASE_DIR = os.path.dirname(os.path.abspath(__file__))
UPLOAD_DIR = os.path.join(BASE_DIR, 'uploads')
os.makedirs(UPLOAD_DIR, exist_ok=True)

app = Flask(__name__)
# CORS 仅允许 Java 主服务调用（默认 127.0.0.1）。
# 通过环境变量 CORS_ALLOWED_ORIGINS 可配置多个来源（逗号分隔）。
_allowed_origins = os.environ.get(
    'CORS_ALLOWED_ORIGINS', 'http://127.0.0.1:8080,http://localhost:8080'
).split(',')
CORS(app, origins=[o.strip() for o in _allowed_origins if o.strip()])
app.config['MAX_CONTENT_LENGTH'] = 16 * 1024 * 1024  # 16MB 上限
app.secret_key = os.environ.get('FLASK_SECRET_KEY', os.urandom(24).hex())


@app.route('/api/health', methods=['GET'])
def health():
    return {"status": "ok", "service": "skillbridge-export"}


@app.route('/api/parse-bookmarks', methods=['POST'])
def api_parse_bookmarks():
    """接收上传的 .docx 文件,解析其表格结构与书签信息。"""
    if 'file' not in request.files:
        return jsonify({"error": "未提供文件 (form field: file)"}), 400
    f = request.files['file']
    if not f.filename:
        return jsonify({"error": "文件名为空"}), 400

    safe_name = secure_filename(f.filename) or 'template.docx'
    if not _is_allowed_docx(safe_name, f):
        return jsonify({"error": "仅支持 .docx 文件"}), 400
    unique_name = f"{uuid.uuid4().hex}_{safe_name}"
    save_path = os.path.join(UPLOAD_DIR, unique_name)
    f.save(save_path)

    try:
        result = parse_bookmarks(save_path)
        return jsonify(result)
    except Exception:
        app.logger.exception("parse_bookmarks 处理失败")
        return jsonify({"error": "服务器内部错误"}), 500
    finally:
        try:
            os.remove(save_path)
        except OSError:
            pass


@app.route('/api/fill-word', methods=['POST'])
def api_fill_word():
    """接收上传的 .docx 模板文件 + JSON data,生成填好的 Word 文件并返回。"""
    if 'template' not in request.files:
        return jsonify({"error": "未提供模板文件 (form field: template)"}), 400
    f = request.files['template']
    if not f.filename:
        return jsonify({"error": "文件名为空"}), 400

    data_json = request.form.get('data', '{}')
    try:
        data = json.loads(data_json)
    except json.JSONDecodeError:
        return jsonify({"error": "data 字段不是合法 JSON"}), 400
    if not isinstance(data, dict):
        return jsonify({"error": "data 字段必须是 JSON 对象"}), 400

    safe_name = secure_filename(f.filename) or 'template.docx'
    if not _is_allowed_docx(safe_name, f):
        return jsonify({"error": "仅支持 .docx 模板文件"}), 400
    unique_name = f"{uuid.uuid4().hex}_{safe_name}"
    save_path = os.path.join(UPLOAD_DIR, unique_name)
    f.save(save_path)

    out_path = None
    try:
        out_path = fill_docx(save_path, data)

        @after_this_request
        def cleanup_temp_file(response):
            try:
                if out_path and os.path.exists(out_path):
                    os.remove(out_path)
            except OSError:
                pass
            return response

        return send_file(
            out_path,
            as_attachment=True,
            download_name=os.path.basename(out_path),
            mimetype='application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        )
    except Exception:
        app.logger.exception("fill_docx 处理失败")
        # 异常路径下显式清理 out_path，防止临时文件残留
        if out_path and os.path.exists(out_path):
            try:
                os.remove(out_path)
            except OSError:
                pass
        return jsonify({"error": "服务器内部错误"}), 500
    finally:
        try:
            os.remove(save_path)
        except OSError:
            pass


def _is_allowed_docx(filename, fileobj=None):
    """验证扩展名和文件魔数是否为 .docx。"""
    if not filename.lower().endswith('.docx'):
        return False
    if fileobj is not None:
        magic = fileobj.read(4)
        fileobj.seek(0)
        if magic != b'PK\x03\x04':  # ZIP 头（.docx = ZIP）
            return False
    return True


if __name__ == '__main__':
    port = int(os.environ.get('FLASK_PORT', '5000'))
    app.run(
        host=os.environ.get('FLASK_HOST', '127.0.0.1'),
        port=port,
        debug=os.environ.get('FLASK_DEBUG', '0') == '1',
    )
