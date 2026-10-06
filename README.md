# Java-- Compiler

A production ready compiler for **Java--**, a subset of Java, translating Java-- source code into JVM bytecode through a multi-stage pipeline:

```
Java-- source → AST → Semantic Analysis → Optimizations → OLLIR → Register Allocation → Jasmin → .class
```
---

## Overview

This compiler translates **Java--** source files into executable **Java Virtual Machine (JVM)** bytecode via Jasmin. The compilation pipeline is modular, robust, and handles every foundational stage:

1. **Syntactic & Lexical Analysis:** Tokenization and grammar interpretation using ANTLR to construct a clean Abstract Syntax Tree (AST).
2. **Semantic Analysis:** Symbol table generation, lexical scope validation, type checking, and explicit error recovery.
3. **Intermediate Representation (IR) & Optimization:** AST-to-IR translation coupled with optimizations (such as constant folding and liveness analysis).
4. **Code Generation:** Translation into valid Jasmin assembly instructions that run on standard JVM runtimes.

---

## Requirements & Environment Setup

Ensure the local development environment meets the following dependencies:
- **Java Development Kit (JDK):** Version 17 or higher
- **Build Tool:** Gradle (wrapper included)

---

## Building & Running

### Build the Project

Compile classes and assemble dependencies using the Gradle wrapper:

```bash
./gradlew build

```

### Run the Compiler

Execute the compiler against a target `.jmm` source file:

```bash
./gradlew run --args="<path-to-source.jmm>"

```

### Execute Tests

Run unit tests and integration tests to verify compiler behavior:

```bash
./gradlew test

```

---

## Overview of the Repository's Structure

The base repository has several folders, the main ones are:

- `src`: The source folder for the project.
- `test`: Folder with private, custom tests for the compiler.
- `test-public`: Public tests.


The remaining folders are:

- `libs`: Libraries in JAR format, required for the project.
- `libs-jmm`: Java code that can be imported in Java-- classes. Contains a `java` folder, with the source code, and a `compiled` folder with the same classes, in compiled format. The build system automatically compiles the files inside the `java` folder and stores them in the `compiled` folder.

---

## Implemented Features

* [x] **Lexer & Parser:** Full grammar coverage for Jmm syntax rules.
* [x] **Symbol Table:** Scoping mechanisms, field/method/variable resolutions.
* [x] **Semantic Analysis:** Comprehensive type-checking and descriptive error messages.
* [x] **Code Generation:** Jasmin bytecode production and execution verification.
* [x] **Optimizations:** Constant folding & propagation.

---

