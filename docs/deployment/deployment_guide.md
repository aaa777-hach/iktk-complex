# Deployment Guide — инструкция по развёртыванию ИКТК

**Проект:** Интегрированный конструкторско-технологический комплекс (ИКТК)  
**Версия:** 1.0.0  
**Дата:** актуально на момент редакции 3 `final_TZ.md`  
**Статус:** утверждено

---

## 1. Обзор

Документ описывает полный процесс развёртывания ИКТК:

- требования к инфраструктуре;
- подготовка окружения;
- сборка Docker-образов;
- развёртывание в Kubernetes;
- настройка БД, очередей, хранилища;
- проверка работоспособности;
- резервное копирование и восстановление;
- обновление и откат;
- диагностика и устранение неисправностей.

### 1.1. Целевые окружения

| Окружение | Namespace | Назначение | Триггер |
|---|---|---|---|
| Development | `iktk-dev` | Локальная разработка | push в `develop` |
| Staging | `iktk-stage` | Предрелизная проверка | push в `release/*` |
| Production | `iktk` | Промышленная эксплуатация | tag `v*.*.*` (вручную) |

---

## 2. Требования к инфраструктуре

### 2.1. Минимальная конфигурация (MVP)

| Компонент | Кол-во | Параметры |
|---|---|---|
| Kubernetes-узлы | 3 | 4 vCPU, 16 GB RAM, 100 GB SSD |
| PostgreSQL | 1 | 2 vCPU, 4 GB RAM, 50 GB SSD |
| RabbitMQ | 1 | 2 vCPU, 4 GB RAM, 20 GB SSD |
| MinIO | 1 | 2 vCPU, 4 GB RAM, 200 GB HDD/SSD |
| Registry (Docker) | внешний | — |

### 2.2. Рекомендуемая конфигурация (Production)

| Компонент | Кол-во | Параметры |
|---|---|---|
| Kubernetes-узлы (control plane) | 3 | 4 vCPU, 16 GB RAM, 100 GB SSD |
| Kubernetes-узлы (worker) | 5+ | 8 vCPU, 32 GB RAM, 200 GB SSD |
| PostgreSQL (HA) | 2 | 4 vCPU, 16 GB RAM, 200 GB SSD |
| RabbitMQ (кластер) | 3 | 4 vCPU, 8 GB RAM, 50 GB SSD |
| MinIO (distributed) | 4 | 4 vCPU, 16 GB RAM, 500 GB SSD |
| Load Balancer | 2 | — |
| Registry (Harbor) | 1 | 4 vCPU, 8 GB RAM, 200 GB SSD |

### 2.3. Программное обеспечение

| ПО | Версия | Назначение |
|---|---|---|
| Kubernetes | 1.28+ | Оркестрация |
| kubectl | 1.28+ | Управление кластером |
| Helm | 3.13+ | Пакетный менеджер (опционально) |
| Docker | 24+ | Сборка образов |
| GitLab CI | 16+ | CI/CD |
| PostgreSQL | 16 | СУБД |
| RabbitMQ | 3.13 | Очереди |
| MinIO | RELEASE.2024+ | Объектное хранилище |

### 2.4. Сетевые требования

| Порт | Протокол | Назначение |
|---|---|---|
| 443 | HTTPS | Внешний доступ (UI, API) |
| 80 | HTTP | Редирект на HTTPS |
| 5432 | TCP | PostgreSQL (внутренний) |
| 5672 | TCP | RabbitMQ AMQP (внутренний) |
| 15672 | TCP | RabbitMQ Management (внутренний) |
| 9000 | TCP | MinIO API (внутренний) |
| 9001 | TCP | MinIO Console (внутренний) |
| 8080 | TCP | API Gateway (внутренний) |
| 8081 | TCP | Management-порты сервисов (внутренний) |

---

## 3. Подготовка окружения

### 3.1. Настройка Kubernetes

```bash
# Проверка доступа к кластеру
kubectl cluster-info
kubectl get nodes

# Создание namespace
kubectl apply -f - <<EOF
apiVersion: v1
kind: Namespace
metadata:
  name: iktk
EOF

# Создание service account для CI/CD
kubectl -n iktk create serviceaccount gitlab-deployer
kubectl -n iktk create clusterrolebinding gitlab-deployer-admin \
  --clusterrole=cluster-admin \
  --serviceaccount=iktk:gitlab-deployer

# Получение kubeconfig для CI/CD
kubectl -n iktk create token gitlab-deployer --duration=8760h