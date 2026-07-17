# How to Use the Distributed Job Scheduler

This document explains how to schedule and interact with the various sample jobs built into the system. All jobs can be submitted via the REST API to the `submission-service` running on port 8081.

## 1. Submitting Jobs

The API accepts job submissions at `POST /api/v1/jobs`. Below are the different types of jobs available, along with their sample payloads and the `curl` commands to schedule them.

### ✉️ Email Send (`email.send`)
Sends a real email using SMTP (if `.env` is configured) or simulates sending.

```bash
curl -s -X POST http://localhost:8081/api/v1/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "type": "email.send",
    "payload": {
      "to": "user@example.com",
      "subject": "Welcome!",
      "body": "Your account has been created."
    },
    "priority": 1,
    "maxRetries": 3,
    "timeoutMs": 5000
  }'
```

### 📊 Report Generation (`report.generate`)
Simulates a heavy report generation process.

```bash
curl -s -X POST http://localhost:8081/api/v1/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "type": "report.generate",
    "payload": {
      "reportType": "monthly_sales",
      "format": "pdf",
      "dateRange": "2026-06-01_2026-06-30"
    },
    "priority": 3,
    "maxRetries": 2
  }'
```

### 🔔 Push Notification (`notification.push`)
Simulates sending a push notification to a mobile device or web client.

```bash
curl -s -X POST http://localhost:8081/api/v1/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "type": "notification.push",
    "payload": {
      "userId": "user_98765",
      "deviceType": "ios",
      "message": "Your report is ready to download."
    },
    "priority": 1
  }'
```

### 💾 Data Export (`data.export`)
Simulates a long-running data export task.

```bash
curl -s -X POST http://localhost:8081/api/v1/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "type": "data.export",
    "payload": {
      "targetBucket": "s3://jobqueue-exports/users",
      "format": "csv",
      "includeDeleted": false
    },
    "priority": 4,
    "maxRetries": 5
  }'
```

### 🧪 Test Job (`test`)
A quick test job used for smoke testing and verifying the worker is processing the queue.

```bash
curl -s -X POST http://localhost:8081/api/v1/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "type": "test",
    "payload": {
      "source": "cli-test",
      "timestamp": "'$(date +%s)'"
    },
    "priority": 5
  }'
```

---

## 2. General Job Submission Pattern

You can submit **any** custom job to the system as long as there is a corresponding worker handler registered in `HandlerRegistry.java`. 

The general `curl` pattern for submitting any job is:

```bash
curl -s -X POST http://localhost:8081/api/v1/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "type": "<your-job-type-name>",
    "payload": {
      // Any valid JSON object containing parameters your handler needs
      "key": "value"
    },
    "priority": <1-5>,             // Optional: Defaults to 3 (1 = highest priority)
    "maxRetries": <number>,        // Optional: Defaults to 3
    "timeoutMs": <milliseconds>,   // Optional: Defaults to no timeout (0)
    "idempotencyKey": "<string>"   // Optional: Provide to prevent duplicate processing
  }'
```

**Key Fields:**
- `type` (String, Required): The exact string matching the registered job handler (e.g., `video.encode`, `invoice.generate`).
- `payload` (JSON, Required): A nested JSON object containing all the arbitrary parameters your specific job requires. The worker will receive this payload as a serialized string.
- `priority` (Int, Optional): Determines queue priority. `1` is the highest priority (processed first), `5` is the lowest. Default is `3`.
- `maxRetries` (Int, Optional): How many times to retry the job upon failure with exponential backoff. Default is `3`.
- `idempotencyKey` (String, Optional): A unique client-provided string. If a job is submitted twice with the same key, the system will return HTTP 409 Conflict to prevent accidental double execution.

---

## 3. Checking Job Status

When you submit a job, the API will return a `202 Accepted` response with a `jobId`:

```json
{
  "jobId": "c92f58b0-81bd-4217-b08e-c35f790c37c2",
  "type": "report.generate",
  "status": "QUEUED",
  "priority": 3,
  "submittedAt": "2026-07-17T10:45:00Z",
  "message": "Job accepted and enqueued"
}
```

You can check the current status of the job (`QUEUED`, `PROCESSING`, `COMPLETED`, `FAILED`, `DEAD`) using this ID:

```bash
curl -s http://localhost:8081/api/v1/jobs/c92f58b0-81bd-4217-b08e-c35f790c37c2 | jq .
```

---

## 4. Worker Status & Queue Depth

You can view the active job queue depths (for priorities 1–5) and how many jobs are currently being processed by the worker:

```bash
curl -s http://localhost:8082/api/v1/worker/status | jq .
```

*Note: The worker service runs on port `8082`.*
