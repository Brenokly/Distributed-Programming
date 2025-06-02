# ☁️ Projeto de Simulação de Coleta de Dados Climáticos Distribuídos

## Disciplina: Programação Distribuída 🌐

### Curso: Ciência da Computação 💻

### Universidade: Ufersa - Universidade Federal Rural do Semi-Árido 🌱

### Ano: 2025 📅

## 📖 Sumário

1. [Introdução](#introducao)
2. [Objetivos](#objetivos)
3. [Prática Unidade 1 - Simulação de um Sistema Distribuído para Coleta de Dados Climáticos](#pratica-unidade-1)

   * [Objetivo](#objetivo)
   * [Conceitos-Chave](#conceitos-chave)
   * [Requisitos do Sistema](#requisitos-do-sistema)
   * [Fluxo da Simulação](#fluxo-da-simulacao)
4. [Metodologia](#metodologia)
5. [Estrutura do Projeto](#estrutura-do-projeto)
6. [Referências Bibliográficas](#referencias-bibliograficas)

---

## 📚 Introdução <a id="introducao"></a>

Este projeto é uma simulação de um sistema distribuído para coleta e gerenciamento de dados climáticos, desenvolvido como atividade da Unidade 1 da disciplina de Programação Distribuída. O sistema simula drones que coletam dados climáticos e os enviam para centros de dados e usuários utilizando conceitos e técnicas de programação distribuída.

## 🎯 Objetivos <a id="objetivos"></a>

O objetivo da disciplina é capacitar os alunos na construção de sistemas distribuídos, aplicando conceitos de comunicação entre processos, concorrência, paralelismo, balanceamento de carga, transmissão multicast e uso de serviços de execução concorrente.

---

## 🛰️ Prática Unidade 1 - Simulação de um Sistema Distribuído para Coleta de Dados Climáticos <a id="pratica-unidade-1"></a>

### 🎯 Objetivo <a id="objetivo"></a>

Desenvolver um sistema distribuído capaz de simular a coleta de dados climáticos por drones, armazenamento centralizado dos dados e distribuição desses dados a usuários através de multicast, aplicando técnicas de balanceamento de carga e programação concorrente.

### 🏷️ Conceitos-Chave <a id="conceitos-chave"></a>

* **Sockets:** TCP e UDP, incluindo multicast.
* **Balanceamento de Carga:** Distribuição de requisições entre servidores.
* **Multicast:** Comunicação eficiente para grupos de usuários.
* **Programação Concorrente:** Threads, executores, lambdas e streams.
* **Programação Funcional: Streams, BinaryOperator, 

### 📜 Requisitos do Sistema <a id="requisitos-do-sistema"></a>

* **Drones:**

  * 4 drones simulam coleta de dados climáticos em regiões (Norte, Sul, Leste e Oeste).
  * Cada drone gera dados no formato específico:

    * Norte → `pressao-radiacao-temperatura-umidade`
    * Sul → `(pressao;radiacao;temperatura;umidade)`
    * Leste → `{pressao,radiacao,temperatura,umidade}`
    * Oeste → `pressao#radiacao#temperatura#umidade`
  * Drones transmitem dados para um **Servidor Multicast**.

* **Servidor de Base de Dados:**

  * Recebe dados do Servidor Multicast.
  * Encaminha os dados via **Unicast TCP** para um dos dois **Servidores de Dados**, aplicando balanceamento de carga (escolha aleatória).

* **Servidores de Dados:**

  * Recebem dados do servidor de base de dados.
  * Armazenam os dados no formato padronizado: `[temperatura//umidade//pressao//radiacao]`.
  * Enviam atualizações para um grupo **Multicast** específico de cada servidor.

* **Usuários:**

  * Se conectam ao sistema por meio de um **Servidor Localizador/Balanceador**, que informa o IP do grupo multicast de um dos servidores de dados.
  * Recebem os dados climáticos atualizados em tempo real via multicast.

### 🔄 Fluxo da Simulação <a id="fluxo-da-simulacao"></a>

1. **Inicialização:**

   * Drones iniciam a simulação de coleta de dados aleatórios (com faixas realistas).
   * Dados são enviados para o **Servidor Multicast** central.

2. **Processamento:**

   * O Servidor de Base de Dados recebe dados multicast, realiza a transformação para o formato padronizado e faz o balanceamento enviando por TCP para um dos dois servidores de dados.

3. **Distribuição:**

   * Cada servidor de dados publica as informações em um grupo multicast próprio.
   * Usuários se conectam a um grupo multicast, obtendo o IP através do servidor localizador, e recebem atualizações em tempo real.

4. **Encerramento:**

   * A simulação roda por um tempo definido (ex.: 3 minutos).
   * Ao final, gera-se um log dos dados transmitidos, coletados e processados.

---

## 🏫 Metodologia <a id="metodologia"></a>

* **Técnicas:** Desenvolvimento incremental, aulas teóricas e práticas, testes locais e simulações.
* **Tecnologias:** Java, Sockets (TCP/UDP/Multicast), Programação Concorrente (Executors, Threads, Lambdas).
* **Avaliação:** Entregas práticas, demonstração de funcionamento do sistema, qualidade do código, uso correto dos conceitos de programação distribuída.

---

## 📂 Estrutura do Projeto <a id="estrutura-do-projeto"></a>

```
📁 Distributed-Programming
 ├── 📁 src
 │   ├── 📁 main/java/org/example
 │   │   ├── 📁 client            # Código dos usuários (clientes que recebem dados)
 │   │   ├── 📁 drones            # Simulação dos drones (Norte, Sul, Leste, Oeste)
 │   │   ├── 📁 locator           # Servidor localizador/balanceador
 │   │   ├── 📁 multicastserver   # Servidor central que recebe dados dos drones
 │   │   ├── 📁 datacenter        # Base de dados que balanceia dados para os servidores
 │   │   ├── 📁 dataservers       # Servidores que armazenam dados e enviam multicast aos usuários
 │   │   ├── 📁 utils             # Utilitários gerais, formatos, logs, transformação de dados
 │   │   │   ├── 📄 DataConverter.java
 │   │   │   ├── 📄 Logger.java
 │   │   │   ├── 📄 RandomGenerator.java
 │   │   │   ├── 📄 Config.java
 │   │   │   └── 📄 Constants.java
 ├── 📄 README.md
 ├── 📄 pom.xml
```

---

## 📚 Referências Bibliográficas <a id="referencias-bibliograficas"></a>

### 📖 Obrigatórias:

* Coulouris, George. *Sistemas distribuídos: conceitos e projeto*. Bookman, 2013.
