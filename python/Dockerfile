FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app.py .
COPY public/ public/
RUN mkdir -p /data
EXPOSE 8000
CMD ["gunicorn", "--workers", "1", "--threads", "4", "-b", "0.0.0.0:8000", "app:app"]
