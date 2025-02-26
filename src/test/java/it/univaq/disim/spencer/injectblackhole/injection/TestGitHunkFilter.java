package it.univaq.disim.spencer.injectblackhole.injection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class TestGitHunkFilter {

    @ParameterizedTest
    @CsvSource({
        "/diff_sane.patch, /diff_sane.patch",
        "/diff_faulty.patch, /diff_faulty_expected.patch"
    })
    public void testFixPatch(String patchFile, String expectedPatchFile) throws IOException, InterruptedException {
        Path patchFilePath = Paths.get(TestGitHunkFilter.class.getResource(patchFile).getPath());
        Path expectedPatchFilePath = Paths.get(TestGitHunkFilter.class.getResource(expectedPatchFile).getPath());
        String patch = Files.readString(patchFilePath);
        String expectedPatch = Files.readString(expectedPatchFilePath);
        String fixedPatch = GitHunkFilter.fixPatch(patch, "Blackhole.consumeCPU");
        assert fixedPatch.equals(expectedPatch) : "Expected:\n" + expectedPatch + "--\n\nActual:\n" + fixedPatch;
    }
}
