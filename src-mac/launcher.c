#ifndef JRE_DIR
#define JRE_DIR "mac-arm64" /* Intel build: -DJRE_DIR=\"mac-x64\" */
#endif
/* Tiny native launcher for the macOS app: starts the bundled JRE on the bundled jar.
 * Built for arm64; the compiled binary is what lives in Contents/MacOS. */
#include <limits.h>
#include <libgen.h>
#include <mach-o/dyld.h>
#include <spawn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>

extern char **environ;

int main(void) {
    char raw[PATH_MAX], exe[PATH_MAX];
    uint32_t size = sizeof(raw);
    if (_NSGetExecutablePath(raw, &size) != 0 || !realpath(raw, exe)) return 1;

    char macos[PATH_MAX];
    strlcpy(macos, dirname(exe), sizeof(macos));

    char res[PATH_MAX], java[PATH_MAX], jar[PATH_MAX];
    snprintf(res, sizeof(res), "%s/../Resources", macos);
    snprintf(java, sizeof(java), "%s/" JRE_DIR "/Contents/Home/bin/java", res);
    snprintf(jar, sizeof(jar), "%s/ApkExtractor.jar", res);

    char *argv[] = { java, "-Xdock:name=AAB to APK Extractor", "-jar", jar, NULL };
    pid_t pid;
    if (posix_spawn(&pid, java, NULL, NULL, argv, environ) != 0) return 2;
    int status = 0;
    waitpid(pid, &status, 0);
    return WIFEXITED(status) ? WEXITSTATUS(status) : 3;
}
