# Order Management System — Backend

![CI](https://github.com/WiltonReis/order-system/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-blue?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen?logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?logo=postgresql)
![License](https://img.shields.io/badge/License-MIT-yellow)

REST API para gerenciamento de pedidos, produtos e usuários. Desenvolvida com Java 21 e Spring Boot 3, com autenticação stateless via JWT e controle de acesso baseado em roles.

---

## 📑 Sumário

- [Sobre o projeto](#-sobre-o-projeto)
- [Funcionalidades principais](#-funcionalidades-principais)
- [Regras de negócio](#-regras-de-negócio)
- [Arquitetura](#️-arquitetura)
- [Tecnologias utilizadas](#️-tecnologias-utilizadas)
- [Frontend](#-frontend)
- [Como rodar o projeto](#️-como-rodar-o-projeto)
- [Autenticação](#-autenticação)
- [Endpoints da API](#-endpoints-da-api)
- [Melhorias futuras](#-melhorias-futuras)
- [Licença](#-licença)

---

## 📖 Sobre o projeto

O **Order Management System (OMS)** é uma API REST para gestão de pedidos em ambientes de varejo ou serviços. Permite criar e acompanhar pedidos, gerenciar produtos e controlar o acesso de usuários com dois níveis de permissão (ADMIN e USER).

A aplicação foi projetada com separação clara de camadas (Controller → Service → Repository), autenticação stateless com JWT armazenado em cookie `httpOnly` e paginação em todos os endpoints de listagem.

---

## 🚀 Funcionalidades principais

- **Autenticação** — Login com JWT via cookie `httpOnly`; logout limpa o cookie
- **Pedidos** — Criação, listagem paginada, detalhe com itens, transições de status (OPEN → COMPLETED / CANCELED), aplicação de desconto e gerenciamento de itens
- **Produtos** — CRUD completo com atualização granular de preço
- **Usuários** — CRUD de usuários com controle de roles (restrito a ADMIN)
- **Paginação** — Todos os endpoints de listagem suportam `page`, `size` e `sort`
- **Código de pedido** — Gerado via sequence PostgreSQL (atômico, sem colisão)

---

## 🧠 Regras de negócio

| Regra | Detalhe |
|---|---|
| Status de pedido | Somente pedidos `OPEN` aceitam itens ou desconto |
| Transição de status | `OPEN → COMPLETED` ou `OPEN → CANCELED`; não é possível reverter |
| Desconto | Aplicado como valor absoluto; total nunca fica negativo |
| Preço de item | Snapshot do preço do produto no momento da adição |
| Desconto — acesso | Somente `ADMIN` pode aplicar desconto (`@PreAuthorize`) |
| Usuários — acesso | Todos os endpoints `/users/**` exigem role `ADMIN` |
| Produtos — escrita | `POST`, `PUT`, `PATCH`, `DELETE` em `/products/**` exigem role `ADMIN` |
| Produtos — leitura | `GET /products` e `GET /products/**` requerem autenticação |
| Criação de pedido | Qualquer usuário autenticado pode criar pedidos |
| customerName | Opcional; espaços extras são removidos antes de persistir |

---

## 🏗️ Arquitetura

```
┌─────────────────────────────────────────────┐
│                   Client                    │
│         (Frontend / Ferramenta HTTP)        │
└──────────────────┬──────────────────────────┘
                   │ HTTP + Cookie httpOnly (JWT)
┌──────────────────▼──────────────────────────┐
│              Spring Boot API                │
│                                             │
│  ┌──────────────────────────────────────┐   │
│  │  JwtAuthenticationFilter             │   │
│  │  (valida token a cada requisição)    │   │
│  └──────────────────┬───────────────────┘   │
│                     │                       │
│  ┌──────────────────▼───────────────────┐   │
│  │  Controllers  (camada HTTP)          │   │
│  │  AuthController  /auth               │   │
│  │  OrderController /orders             │   │
│  │  ProductController /products         │   │
│  │  UserController  /users              │   │
│  └──────────────────┬───────────────────┘   │
│                     │                       │
│  ┌──────────────────▼───────────────────┐   │
│  │  Services  (regras de negócio)       │   │
│  │  AuthService / OrderService          │   │
│  │  ProductService / UserService        │   │
│  └──────────────────┬───────────────────┘   │
│                     │                       │
│  ┌──────────────────▼───────────────────┐   │
│  │  Repositories  (Spring Data JPA)     │   │
│  │  JPQL com JOIN FETCH (sem N+1)       │   │
│  └──────────────────┬───────────────────┘   │
└─────────────────────┼───────────────────────┘
                      │ JDBC
┌─────────────────────▼───────────────────────┐
│              PostgreSQL                     │
└─────────────────────────────────────────────┘
```

**Pacotes principais:**

```
src/main/java/com/ordersystem/
├── config/          # SecurityConfig, DataInitializer
├── controller/      # Camada HTTP (REST)
├── dto/
│   ├── request/     # Objetos de entrada
│   └── response/    # Objetos de saída
├── entity/          # Entidades JPA
├── enums/           # OrderStatus, Role
├── exception/       # BusinessException, ResourceNotFoundException, GlobalExceptionHandler
├── repository/      # Interfaces Spring Data JPA
├── security/        # JwtTokenProvider, JwtAuthenticationFilter, UserDetailsServiceImpl
└── service/         # Lógica de negócio
```

---

## 🛠️ Tecnologias utilizadas

| Tecnologia | Versão | Uso |
|---|---|---|
| Java | 21 | Linguagem |
| Spring Boot | 3.2.5 | Framework principal |
| Spring Security | 6.x | Autenticação e autorização |
| Spring Data JPA | 3.x | Acesso a dados |
| Hibernate | 6.x | ORM |
| PostgreSQL | 16 | Banco de dados |
| jjwt (JJWT) | 0.12.3 | Geração e validação de tokens JWT |
| Lombok | latest | Redução de boilerplate |
| Maven | 3.9+ | Build e gerenciamento de dependências |
| JUnit 5 + Mockito | latest | Testes unitários |

---

## 🔗 Frontend

O frontend deste projeto é uma aplicação React separada, disponível em:

**[github.com/WiltonReis/order-system-frontend](https://github.com/WiltonReis/order-system-frontend)**

> O backend expõe uma API REST completa e pode ser consumido por qualquer cliente HTTP.

---

## ⚙️ Como rodar o projeto

### Pré-requisitos

- Java 21+
- Maven 3.9+
- PostgreSQL 14+

### Variáveis de ambiente

| Variável | Descrição | Padrão (dev) |
|---|---|---|
| `DB_USERNAME` | Usuário do banco | `postgres` |
| `DB_PASSWORD` | Senha do banco | `postgres` |
| `JWT_SECRET` | Segredo JWT em Base64 (mín. 32 bytes) | valor de dev embutido |
| `COOKIE_SECURE` | Define o atributo `Secure` no cookie JWT | `false` |

> **Produção:** nunca use os valores padrão. Defina `JWT_SECRET` com uma chave aleatória forte e `COOKIE_SECURE=true` com HTTPS.

Exemplo para gerar um `JWT_SECRET` seguro:
```bash
openssl rand -base64 32
```

### Banco de dados

1. Crie o banco e a sequence de pedidos:

```sql
CREATE DATABASE order_system;

\c order_system

CREATE SEQUENCE order_code_seq
    START 1
    INCREMENT 1
    MAXVALUE 99999
    CYCLE;
```

2. O schema das tabelas é criado automaticamente pelo Hibernate na primeira execução (`ddl-auto: update`).

3. Um usuário `admin` com senha `123456` é criado automaticamente pelo `DataInitializer` caso não exista.

> **Produção:** substitua `ddl-auto: update` por `validate` e utilize Flyway para gerenciar migrations.

### Execução

```bash
# Clone o repositório
git clone https://github.com/WiltonReis/order-system.git
cd order-system/order-system-backend

# Com variáveis de ambiente definidas no shell
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export JWT_SECRET=<sua-chave-base64>

# Build e execução
mvn spring-boot:run
```

A API estará disponível em `http://localhost:8080`.

### Testes

```bash
mvn test
```

---

## 🔐 Autenticação

A autenticação é baseada em **JWT (JSON Web Token)** com fluxo stateless:

1. O cliente envia `POST /auth/login` com username e password
2. O backend valida as credenciais, gera um token JWT e o retorna no corpo **e** em um cookie `httpOnly` chamado `oms.token`
3. Requisições subsequentes devem enviar o cookie automaticamente (navegadores) **ou** incluir o header `Authorization: Bearer <token>`
4. `POST /auth/logout` expira o cookie imediatamente

```
┌────────┐        POST /auth/login        ┌─────────┐
│ Client │ ──────────────────────────────► │   API   │
│        │ ◄────── Set-Cookie: oms.token ── │         │
│        │                                 │         │
│        │   GET /orders (cookie enviado)  │         │
│        │ ──────────────────────────────► │         │
│        │ ◄────── 200 OK ─────────────── │         │
└────────┘                                 └─────────┘
```

**Roles disponíveis:**

| Role | Permissões |
|---|---|
| `ADMIN` | Acesso total: usuários, produtos (escrita), desconto em pedidos |
| `USER` | Criar e gerenciar pedidos, visualizar produtos |

---

## 📡 Endpoints da API

> Todos os endpoints (exceto `/auth/**`) exigem autenticação via cookie ou header `Authorization`.

### Auth

| Método | Endpoint | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/auth/login` | Público | Autentica e retorna JWT |
| `POST` | `/auth/logout` | Autenticado | Expira o cookie JWT |

**POST /auth/login — Request:**
```json
{
  "username": "admin",
  "password": "123456"
}
```

**POST /auth/login — Response `200 OK`:**
```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "token": "eyJhbGci...",
  "type": "Bearer",
  "username": "admin",
  "role": "ADMIN"
}
```

---

### Users `🔒 ADMIN`

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/users` | Cria usuário |
| `GET` | `/users?page=0&size=20` | Lista usuários paginado |
| `PUT` | `/users/{id}` | Atualiza dados do usuário |
| `PATCH` | `/users/{id}/role` | Atualiza apenas a role |
| `DELETE` | `/users/{id}` | Remove usuário |

**POST /users — Request:**
```json
{
  "username": "joao",
  "password": "senha123",
  "role": "USER"
}
```

**Response `201 Created`:**
```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "username": "joao",
  "role": "USER"
}
```

---

### Products

| Método | Endpoint | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/products` | ADMIN | Cria produto |
| `GET` | `/products?page=0&size=20` | Autenticado | Lista produtos paginado |
| `GET` | `/products/all` | Autenticado | Lista todos os produtos (sem paginação) |
| `PUT` | `/products/{id}` | ADMIN | Atualiza produto completo |
| `PATCH` | `/products/{id}` | ADMIN | Atualiza apenas o preço |
| `DELETE` | `/products/{id}` | ADMIN | Remove produto |

**POST /products — Request:**
```json
{
  "name": "Café Espresso",
  "description": "Café 100% arábica",
  "price": 8.50
}
```

**Response `201 Created`:**
```json
{
  "id": "a1b2c3d4-...",
  "name": "Café Espresso",
  "description": "Café 100% arábica",
  "price": 8.50,
  "imageUrl": null
}
```

---

### Orders

| Método | Endpoint | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/orders` | Autenticado | Cria pedido |
| `GET` | `/orders?page=0&size=20` | Autenticado | Lista pedidos paginado |
| `GET` | `/orders/details?page=0&size=20` | Autenticado | Lista pedidos com itens |
| `GET` | `/orders/active?page=0&size=20` | Autenticado | Pedidos com status OPEN |
| `GET` | `/orders/history?page=0&size=20` | Autenticado | Pedidos COMPLETED e CANCELED |
| `GET` | `/orders/{id}` | Autenticado | Detalhe de um pedido |
| `PUT` | `/orders/{id}` | ADMIN | Aplica desconto |
| `PUT` | `/orders/{id}/complete` | Autenticado | Conclui pedido |
| `PUT` | `/orders/{id}/cancel` | Autenticado | Cancela pedido |
| `DELETE` | `/orders/{id}` | Autenticado | Remove pedido |
| `POST` | `/orders/{id}/items` | Autenticado | Adiciona item ao pedido |
| `PUT` | `/orders/{id}/items/{itemId}` | Autenticado | Atualiza quantidade do item |
| `DELETE` | `/orders/{id}/items/{itemId}` | Autenticado | Remove item do pedido |

**POST /orders — Request:**
```json
{
  "customerName": "Maria Silva"
}
```

**Response `201 Created`:**
```json
{
  "id": "b2c3d4e5-...",
  "orderCode": "00042",
  "status": "OPEN",
  "createdAt": "2026-04-27T10:30:00",
  "total": 0.00,
  "discount": 0.00,
  "customerName": "Maria Silva",
  "completedAt": null,
  "canceledAt": null,
  "completedByUsername": null,
  "canceledByUsername": null
}
```

**POST /orders/{id}/items — Request:**
```json
{
  "productId": "a1b2c3d4-...",
  "quantity": 2
}
```

**PUT /orders/{id} — Request (aplicar desconto):**
```json
{
  "discount": 5.00
}
```

---

## 📊 Melhorias futuras

Itens identificados na auditoria técnica e planejados para sprints futuros:

| Prioridade | Item | Descrição |
|---|---|---|
| 🔴 Alta | Refresh token | Tokens de curta duração + refresh token em cookie `httpOnly` |
| 🔴 Alta | Rate limiting | Proteção contra força bruta no `/auth/login` com Bucket4j |
| 🔴 Alta | Revogação de JWT | Blacklist em Redis para invalidar tokens após logout ou troca de role |
| 🟡 Média | Migrations com Flyway | Substituir `ddl-auto: update` por migrations versionadas |
| 🟡 Média | Endpoint atômico de pedido | `POST /orders/full` — cria pedido + itens + desconto em uma transação |
| 🟡 Média | Filtros e busca | Filtrar pedidos por status, período, usuário e customerName |
| 🟡 Média | Swagger / OpenAPI | Documentação automática via `springdoc-openapi` |
| 🟡 Média | Spring Actuator | Health check para deploy em ambientes orquestrados |
| 🟢 Baixa | Soft delete | Campo `deletedAt` em pedidos e usuários para reversibilidade |
| 🟢 Baixa | Permissões granulares | Roles adicionais: MANAGER, CASHIER, STOCK |
| 🟢 Baixa | Timestamps de auditoria | Campos `createdAt` / `updatedAt` em User e Product |
| 🟢 Baixa | Upload de imagem | `POST /products/{id}/image` com armazenamento em S3 / MinIO |

---

## 📄 Licença

Este projeto está licenciado sob a [MIT License](LICENSE).
