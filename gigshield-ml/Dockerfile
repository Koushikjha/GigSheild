FROM python:3.12-slim

WORKDIR /app

# System deps for scikit-learn/pandas wheels build/runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .
RUN chmod +x entrypoint.sh

ENV PYTHONUNBUFFERED=1 \
    ML_PORT=5000

EXPOSE 5000

HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=5 \
    CMD curl -f http://localhost:5000/health || exit 1

ENTRYPOINT ["./entrypoint.sh"]
