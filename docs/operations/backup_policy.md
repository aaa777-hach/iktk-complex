# Backup Policy — политика резервного копирования ИКТК

**Проект:** Интегрированный конструкторско-технологический комплекс (ИКТК)  
**Кодовое имя:** `iktk-complex`  
**Версия ТЗ:** `final_TZ.md` (ред. 3)  
**Версия документа:** 1.0  
**Статус:** актуально  
**Ответственный:** DevOps-инженер, Администратор БД

---

## 1. Назначение

Документ описывает политику резервного копирования и восстановления данных ИКТК. Используется для:

- обеспечения сохранности критичных данных;
- минимизации потерь при сбоях;
- выполнения требований по непрерывности бизнеса;
- регламентации процедур бэкапа и восстановления;
- проведения регулярных проверок восстановления.

**Область действия:** все данные ИКТК — PostgreSQL, MinIO, конфигурации Kubernetes, секреты, документация.

---

## 2. Целевые показатели (RTO/RPO)

### 2.1. Определения

- **RTO (Recovery Time Objective)** — целевое время восстановления после сбоя.
- **RPO (Recovery Point Objective)** — целевая точка восстановления (сколько данных допустимо потерять).

### 2.2. Целевые показатели по компонентам

| Компонент | RTO | RPO | Критичность |
|---|---|---|---|
| PostgreSQL (основная БД) | ≤ 1 час | ≤ 15 минут | Критичная |
| MinIO (объектное хранилище) | ≤ 2 часа | ≤ 1 час | Критичная |
| Конфигурации K8s | ≤ 30 минут | ≤ 24 часа | Высокая |
| Секреты (Secrets) | ≤ 30 минут | ≤ 24 часа | Критичная |
| Документация | ≤ 4 часа | ≤ 24 часа | Средняя |
| Логи (Loki) | ≤ 24 часа | ≤ 24 часа | Низкая |

---

## 3. Что резервируется

### 3.1. PostgreSQL

| Объект | Метод | Частота | Хранение |
|---|---|---|---|
| Полный дамп (`pg_dump --format=custom`) | CronJob | Ежедневно, 02:00 | 90 дней |
| WAL-архив (непрерывный) | `archive_mode=on` + `archive_command` | Непрерывно | 30 дней |
| Схема (`pg_dump --schema-only`) | CronJob | Еженедельно, Вс 03:00 | 365 дней |
| Роли и права (`pg_dumpall --roles-only`) | CronJob | Еженедельно | 365 дней |
| Конфигурация (`postgresql.conf`, `pg_hba.conf`) | ConfigMap + Git | При изменении | Бессрочно |

**Целевые таблицы:**
- `iktk.*` — бизнес-данные (проекты, ТП, этапы, BOM, оборудование, рекомендации, версии, аудит, решения);
- `iktk_ref.*` — справочники;
- `iktk_audit.*` — аудит.

### 3.2. MinIO

| Бакет | Метод | Частота | Хранение |
|---|---|---|---|
| `iktk-files` | `mc mirror` в `iktk-backups` | Ежедневно, 03:00 | 90 дней |
| `iktk-incoming` | `mc mirror` в `iktk-backups` | Ежедневно | 30 дней |
| `iktk-logs` | `mc mirror` в `iktk-backups` | Ежедневно | 180 дней |
| `iktk-backups` | Копирование на внешнее хранилище | Еженедельно | 365 дней |

### 3.3. Kubernetes

| Объект | Метод | Частота |
|---|---|---|
| Манифесты (Deployments, Services, ConfigMaps) | `kubectl get -o yaml` → Git | При изменении |
| Secrets | Sealed Secrets / Vault (шифрование) → Git | При изменении |
| PVC-снапшоты | CSI VolumeSnapshot | Ежедневно |
| Helm-релизы (если используются) | `helm get values` → Git | При изменении |

### 3.4. Конфигурации и документация

| Объект | Метод | Частота |
|---|---|---|
| Репозиторий с кодом | Git (удалённый origin) | При каждом коммите |
| Документация (`docs/`) | Git | При каждом коммите |
| CI/CD-конфигурации | Git | При каждом коммите |
| Docker-образы | Registry с retention-политикой | При сборке |

### 3.5. Секреты

| Объект | Метод | Частота |
|---|---|---|
| JWT-секрет | Vault / защищённое хранилище | При ротации |
| Пароли БД | Vault / защищённое хранилище | При ротации |
| Ключи MinIO | Vault / защищённое хранилище | При ротации |
| TLS-сертификаты | cert-manager + бэкап | При обновлении |

---

## 4. Расписание бэкапов

### 4.1. Ежедневно

| Время | Что | Куда |
|---|---|---|
| 02:00 | PostgreSQL full dump | MinIO `iktk-backups/postgres/` |
| 03:00 | MinIO mirror `iktk-files` | MinIO `iktk-backups/files-YYYYMMDD/` |
| 04:00 | PVC-снапшоты | CSI-хранилище |
| 05:00 | Проверка целостности бэкапов | Логи + алерт |

### 4.2. Еженедельно

| День | Время | Что |
|---|---|---|
| Вс | 03:00 | PostgreSQL schema-only |
| Вс | 03:30 | PostgreSQL roles-only |
| Вс | 04:00 | Копирование `iktk-backups` на внешнее хранилище |
| Пн | 05:00 | Проверка восстановления (тестовый стенд) |

### 4.3. Ежемесячно

| Что | Действие |
|---|---|
| Полный аудит бэкапов | Проверка всех дампов, целостности, доступности |
| Тест восстановления БД | Полное восстановление на stage |
| Тест восстановления MinIO | Восстановление выборочных объектов |
| Отчёт по бэкапам | Руководителю проекта |

### 4.4. Ежеквартально

| Что | Действие |
|---|---|
| Пересмотр политики | Актуализация RTO/RPO, retention |
| Обновление процедур | Регламенты, runbook |
| Аудит доступа к бэкапам | Проверка прав |

---

## 5. Реализация

### 5.1. PostgreSQL: CronJob

**Манифест:** `deployment/k8s/postgres-backup-cronjob.yaml` (позиция 60).

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: postgres-backup
  namespace: iktk
spec:
  schedule: "0 2 * * *"
  concurrencyPolicy: Forbid
  successfulJobsHistoryLimit: 7
  failedJobsHistoryLimit: 3
  startingDeadlineSeconds: 600
  jobTemplate:
    spec:
      backoffLimit: 2
      activeDeadlineSeconds: 7200
      template:
        spec:
          restartPolicy: OnFailure
          containers:
            - name: backup
              image: postgres:16-alpine
              command:
                - /bin/sh
                - -c
                - |
                  set -e
                  TIMESTAMP=$(date +%Y%m%d_%H%M%S)
                  FILENAME="iktk_backup_${TIMESTAMP}.dump"

                  echo "[$(date)] Начало резервного копирования: ${FILENAME}"

                  pg_dump \
                    --format=custom \
                    --compress=9 \
                    --verbose \
                    --no-owner \
                    --no-privileges \
                    -f "/tmp/${FILENAME}"

                  echo "[$(date)] Дамп создан: $(ls -lh /tmp/${FILENAME})"

                  # Загрузка в MinIO
                  mc alias set iktk "${MINIO_ENDPOINT}" "${MINIO_ACCESS_KEY}" "${MINIO_SECRET_KEY}"
                  mc cp "/tmp/${FILENAME}" "iktk/iktk-backups/postgres/${FILENAME}"

                  echo "[$(date)] Дамп загружен в MinIO"

                  # Очистка старых бэкапов (старше 90 дней)
                  mc rm --recursive --force --older-than 90d \
                    iktk/iktk-backups/postgres/ || true

                  echo "[$(date)] Старые бэкапы очищены"