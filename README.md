# ☁️ Projeto de Simulação de Coleta de Dados Climáticos Distribuídos

## Disciplina: Programação Concorrente e Distribuída 🌐

### Curso: Ciência da Computação 💻

### Universidade: Ufersa - Universidade Federal Rural do Semi-Árido 🌱

### Ano: 2025 📅

---

## 📖 Sumário

1. [Introdução](#introducao)
2. [Objetivos](#objetivos)
3. [Prática Offline 2 - Simulação com Comunicação Indireta](#pratica-offline-2)

   * [Objetivo](#objetivo)
   * [Conceitos-Chave](#conceitos-chave)
   * [Requisitos do Sistema](#requisitos-do-sistema)
   * [Fluxo da Simulação](#fluxo-da-simulacao)
   * [Como Executar](#como-executar)
4. [Metodologia](#metodologia)
5. [Estrutura do Projeto](#estrutura-do-projeto)
6. [Referências Bibliográficas](#referencias-bibliograficas)

---

## 📚 Introdução <a id="introducao"></a>

Este projeto é uma simulação de um sistema distribuído para coleta, processamento e gerenciamento de dados climáticos, desenvolvido como atividade da Prática Offline 2 da disciplina de Programação Concorrente e Distribuída. O sistema simula drones que coletam dados climáticos e os enviam para um Gateway central, que por sua vez os disponibiliza para diferentes tipos de usuários através de sistemas de comunicação indireta, como MQTT e RabbitMQ.

---

## 🎯 Objetivos <a id="objetivos"></a>

O objetivo da disciplina é capacitar os alunos na construção de sistemas distribuídos, aplicando conceitos de comunicação entre processos, concorrência, paralelismo e, nesta prática, com foco em sistemas de mensagens, desacoplamento e comunicação indireta.

---

## 🛰️ Prática Offline 2 - Simulação com Comunicação Indireta <a id="pratica-offline-2"></a>

### 🎯 Objetivo <a id="objetivo"></a>

Desenvolver uma simulação de um sistema distribuído para a coleta, armazenamento e distribuição de dados climáticos, utilizando drones, um gateway central e usuários conectados via MQTT e RabbitMQ, aplicando conceitos de comunicação indireta, concorrência e programação funcional em Java.

---

### 🏷️ Conceitos-Chave <a id="conceitos-chave"></a>

* **Comunicação Indireta:** Desacoplamento espacial e temporal entre os componentes do sistema.
* **MQTT:** Protocolo leve de publicação/assinatura, ideal para telemetria e comunicação em tempo real.
* **RabbitMQ (AMQP):** Broker robusto, usado com Topic Exchange para entrega filtrável de dados.
* **Publish/Subscribe:** Arquitetura onde produtores enviam mensagens sem conhecer os consumidores.
* **Programação Concorrente:** Uso de `ExecutorService` e `ScheduledExecutorService`.
* **Programação Funcional:** Aplicação de Lambdas e Streams para manipulação e transformação de dados.

---

### 📜 Requisitos do Sistema <a id="requisitos-do-sistema"></a>

#### **Drones**

* 4 drones (Norte, Sul, Leste e Oeste) atuam como produtores MQTT.
* Cada um gera dados com formatos diferentes:

  * Norte → `pressao-radiacao-temperatura-umidade`
  * Sul → `(pressao;radiacao;temperatura;umidade)`
  * Leste → `{pressao,radiacao,temperatura,umidade}`
  * Oeste → `pressao#radiacao#temperatura#umidade`
* Publicam em tópicos distintos: `ufersa/pw/climadata/<regiao>`

#### **Gateway (Centro Distribuidor)**

* **Consumidor MQTT:** Inscreve-se em `ufersa/pw/climadata/#`
* **Processador:** Converte todos os formatos para:
  `[regiao//temperatura//umidade//pressao//radiacao//timestamp]`
* **Produtor Dual:**

  * Publica no RabbitMQ via Topic Exchange (`dados.climaticos.<regiao>`)
  * Publica em tópico MQTT processado (`ufersa/pw/gateway/processed_data/<regiao>`)

#### **Usuários**

* **DashboardUser (RabbitMQ):**

  * Menu interativo para inscrição em tópicos (ex: `dados.climaticos.#`)
  * Armazena dados para análise e dashboard

* **RealTimeUser (MQTT):**

  * Conecta ao tópico do Gateway
  * Exibe dados em tempo real e permite geração de relatórios `.txt`

---

### 🔄 Fluxo da Simulação <a id="fluxo-da-simulacao"></a>

1. **Publicação:**
   Drones iniciam e publicam dados a cada 2–5 segundos.

2. **Processamento e Roteamento:**
   Gateway processa os dados e os envia para MQTT e RabbitMQ.

3. **Consumo e Interação:**
   Usuários iniciam, escolhem os dados a monitorar e interagem via menus.

4. **Simulação de Falha:**
   Drones podem simular falha (pausa de envio) e retomar posteriormente.

---

### 🚀 Como Executar <a id="como-executar"></a>

1. **Pré-requisito:**
   Inicie o broker RabbitMQ via Docker:

   ```bash
   docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:management
   ```

2. **Compile o Projeto:**

   ```bash
   mvn clean package
   ```

3. **Execute os Componentes (em terminais separados):**

   * `Gateway`: deve iniciar primeiro
   * `Drones`: execute os desejados (ex: DroneNorte.java)
   * `DashboardUserLauncher` e `RealTimeUserLauncher`: usuários consumidores

---

## 🏫 Metodologia <a id="metodologia"></a>

* **Técnicas:** Desenvolvimento de sistema distribuído desacoplado com mensageria
* **Tecnologias:** Java, MQTT (Paho), RabbitMQ (AMQP), Threads, Executors, Lambdas, Streams
* **Avaliação:** Entregas práticas, demonstrações em múltiplas máquinas e qualidade de código

---

## 📂 Estrutura do Projeto <a id="estrutura-do-projeto"></a>

```
📁 seu-projeto
 ├── 📁 src
 │   ├── 📁 main
 │   │   ├── 📁 java
 │   │   │   └── 📁 com/climate/datas
 │   │   │       ├── 📁 database      # Armazenamento em memória
 │   │   │       │   └── 📄 DataBase.java
 │   │   │       ├── 📁 drone         # Lógica dos drones e seus lançadores
 │   │   │       │   ├── 📁 execute
 │   │   │       │   │   ├── 📄 DroneNorte.java
 │   │   │       │   │   └── ...
 │   │   │       │   └── 📄 Drone.java
 │   │   │       ├── 📁 gateway       # Lógica do Gateway
 │   │   │       │   └── 📄 Gateway.java
 │   │   │       ├── 📁 user          # Lógica dos usuários
 │   │   │       │   ├── 📁 execute
 │   │   │       │   │   ├── 📄 DashboardUserLauncher.java
 │   │   │       │   │   └── 📄 RealTimeUserLauncher.java
 │   │   │       │   ├── 📄 DashboardUser.java
 │   │   │       │   └── 📄 RealTimeUser.java
 │   │   │       └── 📁 utils         # Modelos e utilitários
 │   │   │           └── 📄 ClimateData.java
 │   │   └── 📁 resources
 │   │       └── 📄 logback.xml       # Configuração de logs (opcional)
 ├── 📄 README.md
 └── 📄 pom.xml
```

---

## 📚 Referências Bibliográficas <a id="referencias-bibliograficas"></a>

### 📖 Obrigatórias:

* Coulouris, George. *Sistemas distribuídos: conceitos e projeto*. Bookman, 2013.
* Documentação oficial do RabbitMQ e do protocolo MQTT

---
