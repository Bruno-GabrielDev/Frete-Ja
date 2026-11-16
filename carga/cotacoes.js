import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const ALVO = __ENV.ALVO || 'http://localhost:8080';
const NOME = __ENV.NOME || ALVO;

const consultas = new Counter('consultas_as_transportadoras');
const doCache = new Rate('respostas_do_cache');
const indisponiveis = new Rate('respostas_indisponiveis');
const tempoNoNucleo = new Trend('tempo_no_nucleo', true);

export const options = {
  scenarios: {
    checkout: {
      executor: 'constant-vus',
      vus: Number(__ENV.USUARIOS || 20),
      duration: __ENV.DURACAO || '30s',
    },
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'max'],
};

const ENVIOS = [
  { cepOrigem: '13560-000', cepDestino: '01310-100', pesoKg: 2.5, alturaCm: 20, larguraCm: 30, comprimentoCm: 15 },
  { cepOrigem: '13560-000', cepDestino: '20040-020', pesoKg: 1.2, alturaCm: 10, larguraCm: 20, comprimentoCm: 15 },
  { cepOrigem: '13560-000', cepDestino: '30130-010', pesoKg: 4.0, alturaCm: 30, larguraCm: 30, comprimentoCm: 20 },
  { cepOrigem: '13560-000', cepDestino: '13566-590', pesoKg: 0.8, alturaCm: 10, larguraCm: 15, comprimentoCm: 10 },
  { cepOrigem: '13560-000', cepDestino: '80010-000', pesoKg: 7.5, alturaCm: 40, larguraCm: 30, comprimentoCm: 30 },
];

export default function () {
  const envio = ENVIOS[Math.floor(Math.random() * ENVIOS.length)];
  const resposta = http.post(`${ALVO}/api/cotacoes`, JSON.stringify(envio), {
    headers: { 'Content-Type': 'application/json' },
    timeout: '30s',
  });
  const ok = check(resposta, { 'respondeu 200': (r) => r.status === 200 });
  if (!ok) {
    return;
  }
  const corpo = resposta.json();
  tempoNoNucleo.add(corpo.tempoMs);
  for (const linha of corpo.cotacoes) {
    if (linha.origem === 'PLUGIN') consultas.add(1);
    doCache.add(linha.origem === 'CACHE');
    indisponiveis.add(linha.origem === 'FALHA');
  }
}

export function handleSummary(dados) {
  const m = dados.metrics;
  const valor = (nome, campo) => (m[nome] ? m[nome].values[campo] : 0);
  const n = (x, casas = 0) => {
    const [inteiro, decimal] = Number(x || 0).toFixed(casas).split('.');
    const milhar = inteiro.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    return decimal ? `${milhar},${decimal}` : milhar;
  };
  const ms = (x) => `${n(x, x < 10 ? 1 : 0)} ms`;
  const pct = (x) => `${n(100 * (x || 0), 1)}%`;
  const comparacoes = valor('http_reqs', 'count');
  const linhas = [
    '',
    `=== FreteJá: ${NOME} ===`,
    `Usuários simultâneos ............. ${n(valor('vus_max', 'max'))}`,
    `Comparações feitas ............... ${n(comparacoes)}`,
    `Comparações por segundo .......... ${n(valor('http_reqs', 'rate'), 1)}`,
    `Tempo de resposta (média) ........ ${ms(valor('http_req_duration', 'avg'))}`,
    `Tempo de resposta (95% abaixo de)  ${ms(valor('http_req_duration', 'p(95)'))}`,
    `Consultas às transportadoras ..... ${n(valor('consultas_as_transportadoras', 'count'))}`,
    `Respostas vindas do cache ........ ${pct(valor('respostas_do_cache', 'rate'))}`,
    `Respostas indisponíveis .......... ${pct(valor('respostas_indisponiveis', 'rate'))}`,
    `Erros HTTP ....................... ${pct(valor('http_req_failed', 'rate'))}`,
    '',
  ];
  return { stdout: linhas.join('\n') };
}
