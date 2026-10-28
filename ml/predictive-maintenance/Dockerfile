# Prediction API of the failure-risk model. The model is trained while the image is built (fixed seed,
# about ten seconds), so every image carries the model that matches its code.
FROM python:3.13-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 MODEL_DIR=/app/model
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY factorymind_ml factorymind_ml
RUN python -m factorymind_ml.train --out /app/model
RUN useradd --system --uid 10001 app
USER app
EXPOSE 8090
HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8090/health')"
CMD ["uvicorn", "factorymind_ml.api:app", "--host", "0.0.0.0", "--port", "8090"]
