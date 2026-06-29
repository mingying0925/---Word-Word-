# SkillBridge Python 导出微服务 Dockerfile

FROM python:3.11-slim

RUN groupadd -r skillbridge && useradd -r -g skillbridge -d /app -s /sbin/nologin skillbridge

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
RUN mkdir -p /app/uploads && chown -R skillbridge:skillbridge /app

USER skillbridge
EXPOSE 5000
ENV FLASK_DEBUG=0

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:5000/api/health')"

CMD ["gunicorn", "--bind", "0.0.0.0:5000", "--workers", "2", "--timeout", "120", "--preload", "app:app"]
