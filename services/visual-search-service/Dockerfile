# Visual-search service (Python/CLIP). Built on the EC2 host by
# deploy/aws/deploy-from-artifact.sh when VISUAL_SEARCH_ENABLED=1, or locally:
#   docker build -t ggfix/visual-search-service services/visual-search-service
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    HF_HOME=/opt/hf-cache \
    VS_DATA_DIR=/app/data

WORKDIR /app

# CPU-only torch first: the default PyPI wheels bundle several GB of CUDA
# libraries this service never uses. requirements.txt then finds torch
# already satisfied. Kept above the code COPY so a code-only change reuses
# this (slow) layer.
COPY requirements.txt .
RUN pip install --index-url https://download.pytorch.org/whl/cpu torch==2.14.0 torchvision==0.29.0 \
 && pip install -r requirements.txt

# Bake the CLIP weights into the image so a container starts without
# downloading ~600 MB from Hugging Face. Must match VS_CLIP_MODEL /
# VS_CLIP_PRETRAINED (app/config.py defaults).
RUN python -c "import open_clip; open_clip.create_model_and_transforms('ViT-B-32', pretrained='laion2b_s34b_b79k')" \
 && chmod -R a+rX /opt/hf-cache
# Use only the baked copy at runtime: no Hub round-trip at startup, and no
# attempt to write lock files into a cache the runtime user cannot write.
ENV HF_HUB_OFFLINE=1

COPY app ./app
COPY scripts ./scripts

RUN useradd --system --uid 10001 --home-dir /app visualsearch \
 && mkdir -p /app/data \
 && chown -R visualsearch:visualsearch /app/data
USER visualsearch

EXPOSE 8199
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8199/actuator/health', timeout=4)"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8199"]
