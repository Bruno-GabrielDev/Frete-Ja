#!/usr/bin/env python3
import argparse
import json
import random
import shutil
import sys
import time
import urllib.request
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
ATIVOS = RAIZ / "plugins-ativos"
DISPONIVEIS = RAIZ / "plugins-disponiveis"


def pedir(url, corpo=None):
    dados = None if corpo is None else json.dumps(corpo).encode()
    req = urllib.request.Request(url, data=dados, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode())


def cotar(base, peso):
    envio = {"cepOrigem": "13560-000", "cepDestino": "01310-100", "pesoKg": peso,
             "alturaCm": 20, "larguraCm": 30, "comprimentoCm": 15}
    inicio = time.perf_counter()
    resposta = pedir(base + "/api/cotacoes", envio)
    resposta["tempoCliente"] = (time.perf_counter() - inicio) * 1000
    return resposta


def ids_ativos(base):
    return {t["id"]: t["arquivo"] for t in pedir(base + "/api/transportadoras")}


def esperar(condicao, limite_s=15):
    inicio = time.perf_counter()
    while time.perf_counter() - inicio < limite_s:
        if condicao():
            return (time.perf_counter() - inicio) * 1000
        time.sleep(0.1)
    return None


def peso_novo():
    return round(random.uniform(1.0, 4.9), 3)


def medir_cache(base, repeticoes=10):
    print("\n1) CACHE: a mesma cotação repetida", repeticoes, "vezes")
    peso = peso_novo()
    tempos, consultas = [], 0
    for i in range(repeticoes):
        r = cotar(base, peso)
        tempos.append(r["tempoMs"])
        consultas += sum(1 for c in r["cotacoes"] if c["origem"] == "PLUGIN")
        origens = ", ".join(f'{c["transportadoraId"]}={c["origem"]}' for c in r["cotacoes"])
        print(f"   {i + 1:2d}ª: {r['tempoMs']:5d} ms   {origens}")
    transportadoras = len(r["cotacoes"])
    media_repetidas = sum(tempos[1:]) / (len(tempos) - 1)
    repetidas = "menos de 1 ms" if media_repetidas < 1 else f"{media_repetidas:.1f} ms"
    print(f"   → 1ª cotação: {tempos[0]} ms; repetidas: em média {repetidas}")
    print(f"   → consultas às transportadoras: {consultas} com cache; "
          f"seriam {transportadoras * repeticoes} sem cache")


def medir_circuit_breaker(base, comparacoes=8):
    print("\n2) CIRCUIT BREAKER: Expressa com defeito (API que não responde)")
    ativos = ids_ativos(base)
    if "expressa" in ativos and ativos["expressa"] != "plugin-expressa-instavel.jar":
        print("   pulei: a Expressa corrigida está ativa. Remova com ./scripts/remover.sh expressa e rode de novo.")
        return
    jar = DISPONIVEIS / "plugin-expressa-instavel.jar"
    if not jar.exists():
        print("   pulei: plugin-expressa-instavel.jar não está em plugins-disponiveis/ (rode ./scripts/build.sh)")
        return
    shutil.copy(jar, ATIVOS)
    if esperar(lambda: ids_ativos(base).get("expressa") == "plugin-expressa-instavel.jar") is None:
        print("   pulei: o núcleo não carregou a Expressa instável em 15 s")
        return
    tempos = []
    for i in range(comparacoes):
        r = cotar(base, peso_novo())
        expressa = next(c for c in r["cotacoes"] if c["transportadoraId"] == "expressa")
        estado = next((c["estado"] for c in pedir(base + "/api/circuitos") if c["plugin"] == "expressa"), "?")
        tempos.append(r["tempoMs"])
        print(f"   {i + 1}ª: {r['tempoMs']:5d} ms   expressa={expressa['situacao']:<12} circuito={estado}")
    print(f"   → total de {comparacoes} comparações: {sum(tempos)} ms")
    print(f"   → sem circuit breaker, cada uma esperaria o tempo limite (~2000 ms): ~{comparacoes * 2000} ms")
    (ATIVOS / jar.name).unlink(missing_ok=True)
    esperar(lambda: "expressa" not in ids_ativos(base))
    print("   (Expressa instável removida de plugins-ativos/)")


def medir_extensibilidade(base, plugin):
    print(f"\n3) MICROKERNEL: plugin '{plugin}' entrando com o sistema no ar")
    jar = DISPONIVEIS / f"plugin-{plugin}.jar"
    if not jar.exists():
        print(f"   pulei: {jar.name} não está em plugins-disponiveis/")
        return
    destino = ATIVOS / jar.name
    if destino.exists():
        destino.unlink()
        esperar(lambda: plugin not in ids_ativos(base))
    antes = sorted(ids_ativos(base))
    shutil.copy(jar, ATIVOS)
    tempo = esperar(lambda: plugin in ids_ativos(base))
    if tempo is None:
        print("   o plugin não apareceu em 15 s")
        return
    print(f"   antes: {antes}")
    print(f"   depois: {sorted(ids_ativos(base))}")
    print(f"   → disponível em {tempo:.0f} ms, sem recompilar nem reiniciar o núcleo")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--url", default="http://localhost:8080")
    p.add_argument("--plugin", default="loggi", help="plugin usado na medição de extensibilidade")
    a = p.parse_args()
    try:
        ids_ativos(a.url)
    except Exception as e:
        sys.exit(f"Não consegui falar com o núcleo em {a.url}: {e}")
    medir_cache(a.url)
    medir_circuit_breaker(a.url)
    medir_extensibilidade(a.url, a.plugin)


if __name__ == "__main__":
    main()
