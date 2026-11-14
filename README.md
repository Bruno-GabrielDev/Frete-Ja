# FreteJá

Comparador de frete para lojas virtuais: a loja informa CEP de origem, CEP de destino, peso e
dimensões do pacote, e o sistema devolve as cotações de várias transportadoras, da mais barata
para a mais cara.

Trabalho prático de Arquitetura de Software (IFSP São Carlos — Prof. Dr. Lucas Oliveira).

**Equipe:** Bruno G. A. Silva, Sabrina, Felipe

## Arquitetura planejada

- **Estilo:** Microkernel com descoberta dinâmica de plugins — cada transportadora é um plugin
- **Elementos adicionais:** Circuit Breaking e Caching
- **Stack:** Java 21, Spring Boot 3.3, Maven

Em construção.
