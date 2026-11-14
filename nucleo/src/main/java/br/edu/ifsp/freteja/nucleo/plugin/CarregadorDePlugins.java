package br.edu.ifsp.freteja.nucleo.plugin;

import br.edu.ifsp.freteja.api.Transportadora;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;

public class CarregadorDePlugins implements AutoCloseable {

    private static final Logger LOG = System.getLogger(CarregadorDePlugins.class.getName());

    private static final long JANELA_DE_ESTABILIZACAO_MS = 1_000;

    private final Path diretorio;
    private final RegistroDePlugins registro;
    private final List<OuvinteDePlugins> ouvintes;
    private final ClassLoader pai;
    private final Map<Path, JarCarregado> jars = new HashMap<>();

    private record JarCarregado(long modificadoEm, URLClassLoader loader, List<String> ids) {
    }

    public CarregadorDePlugins(Path diretorio, RegistroDePlugins registro, List<OuvinteDePlugins> ouvintes) {
        this.diretorio = diretorio.toAbsolutePath().normalize();
        this.registro = registro;
        this.ouvintes = List.copyOf(ouvintes);
        this.pai = Transportadora.class.getClassLoader();
    }

    public Path diretorio() {
        return diretorio;
    }

    public synchronized void sincronizar() {
        Set<Path> presentes = listarJars();

        for (Path jar : new HashSet<>(jars.keySet())) {
            if (!presentes.contains(jar)) {
                descarregar(jar, "arquivo removido");
            }
        }

        for (Path jar : presentes) {
            long modificadoEm = ultimaModificacao(jar);
            if (System.currentTimeMillis() - modificadoEm < JANELA_DE_ESTABILIZACAO_MS) {
                continue;
            }
            JarCarregado atual = jars.get(jar);
            if (atual == null) {
                carregar(jar, modificadoEm);
            } else if (atual.modificadoEm() != modificadoEm) {
                descarregar(jar, "arquivo substituído");
                carregar(jar, modificadoEm);
            }
        }
    }

    private void carregar(Path jar, long modificadoEm) {
        URLClassLoader loader = null;
        List<String> ids = new ArrayList<>();
        try {
            loader = new URLClassLoader("plugin:" + jar.getFileName(), new URL[]{jar.toUri().toURL()}, pai);
            for (Transportadora transportadora : ServiceLoader.load(Transportadora.class, loader)) {
                if (transportadora.getClass().getClassLoader() != loader) {
                    continue;
                }
                String id = transportadora.id();
                registro.registrar(new PluginCarregado(transportadora, jar.getFileName().toString(), Instant.now()));
                ids.add(id);
                LOG.log(Level.INFO, "Plugin carregado: {0} ({1}) de {2}", id, transportadora.nome(), jar.getFileName());
                ouvintes.forEach(o -> o.pluginCarregado(id));
            }
            if (ids.isEmpty()) {
                LOG.log(Level.WARNING, "{0} não declara nenhuma Transportadora em META-INF/services", jar.getFileName());
            }
        } catch (ServiceConfigurationError e) {
            LOG.log(Level.ERROR, "Plugin inválido em " + jar.getFileName() + ": " + e.getMessage(), e);
        } catch (Exception | LinkageError e) {
            LOG.log(Level.ERROR, "Falha ao carregar " + jar.getFileName() + ": " + e.getMessage(), e);
        } finally {
            jars.put(jar, new JarCarregado(modificadoEm, loader, List.copyOf(ids)));
        }
    }

    private void descarregar(Path jar, String motivo) {
        JarCarregado carregado = jars.remove(jar);
        if (carregado == null) {
            return;
        }
        for (String id : carregado.ids()) {
            registro.remover(id, jar.getFileName().toString());
            LOG.log(Level.INFO, "Plugin removido: {0} ({1})", id, motivo);
            ouvintes.forEach(o -> o.pluginRemovido(id));
        }
        fechar(carregado.loader());
    }

    private Set<Path> listarJars() {
        try {
            Files.createDirectories(diretorio);
            try (Stream<Path> arquivos = Files.list(diretorio)) {
                Set<Path> jarsNaPasta = new HashSet<>();
                arquivos.filter(p -> p.getFileName().toString().endsWith(".jar"))
                        .filter(Files::isRegularFile)
                        .forEach(p -> jarsNaPasta.add(p.toAbsolutePath().normalize()));
                return jarsNaPasta;
            }
        } catch (IOException e) {
            LOG.log(Level.ERROR, "Não foi possível ler a pasta de plugins " + diretorio, e);
            return Set.copyOf(jars.keySet());
        }
    }

    private static long ultimaModificacao(Path jar) {
        try {
            return Files.getLastModifiedTime(jar).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private static void fechar(URLClassLoader loader) {
        if (loader == null) {
            return;
        }
        try {
            loader.close();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Falha ao fechar class loader: " + e.getMessage());
        }
    }

    @Override
    public synchronized void close() {
        new HashSet<>(jars.keySet()).forEach(jar -> descarregar(jar, "encerramento"));
    }
}
