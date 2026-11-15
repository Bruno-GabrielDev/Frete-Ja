package br.edu.ifsp.freteja.nucleo;

import br.edu.ifsp.freteja.api.Envio;
import br.edu.ifsp.freteja.api.Transportadora;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "br.edu.ifsp.freteja", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

    @ArchTest
    static final ArchRule nucleoNaoConheceImplementacaoDePlugin =
            noClasses().that().resideInAPackage("br.edu.ifsp.freteja.nucleo..")
                    .should().dependOnClassesThat().resideInAPackage("br.edu.ifsp.freteja.plugins..")
                    .because("o núcleo deve funcionar com qualquer transportadora que implemente o contrato");

    @ArchTest
    static final ArchRule contratoNaoDependeDoNucleo =
            noClasses().that().resideInAPackage("br.edu.ifsp.freteja.api..")
                    .should().dependOnClassesThat().resideInAPackage("br.edu.ifsp.freteja.nucleo..")
                    .because("plugins compilam só contra o contrato");

    @ArchTest
    static final ArchRule transportadoraSoEhChamadaPeloExecutorIsolado =
            noClasses().that().resideOutsideOfPackage("br.edu.ifsp.freteja.nucleo.execucao..")
                    .should().callMethod(Transportadora.class, "cotar", Envio.class)
                    .because("uma transportadora com defeito não pode ser consultada sem a proteção do circuit breaker");

    @ArchTest
    static final ArchRule descobertaDePluginsNaoDependeDoSpring =
            noClasses().that().resideInAPackage("br.edu.ifsp.freteja.nucleo.plugin..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                    .because("o mecanismo de extensão do microkernel não deve depender de framework");
}
