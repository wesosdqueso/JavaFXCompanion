using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;

internal static class UpdateLauncher
{
    private static int Main(string[] args)
    {
        bool checkOnly = Array.IndexOf(args, "--check") >= 0;
        try
        {
            Console.Title = "Actualizar JavaFXCompanion";
            string[] settings;
            using (var reader = new StreamReader(Assembly.GetExecutingAssembly().GetManifestResourceStream("settings")))
                settings = reader.ReadToEnd().Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
            string workspace = settings[0];
            string eclipse = settings[1];
            string java = settings[2];
            string repository = Path.GetFullPath(Path.Combine(AppDomain.CurrentDomain.BaseDirectory, ".."));
            for (int index = 0; index < args.Length; index++)
            {
                string option = args[index];
                if (option == "--check") continue;
                if (index + 1 >= args.Length) throw new ArgumentException("Falta el valor de " + option);
                string value = Path.GetFullPath(args[++index]);
                switch (option)
                {
                    case "--workspace": workspace = value; break;
                    case "--eclipse": eclipse = value; break;
                    case "--java": java = value; break;
                    case "--repo": repository = value; break;
                    default: throw new ArgumentException("Opción desconocida: " + option);
                }
            }
            string script = Path.Combine(repository, "tools", "install-live.ps1");
            RequireFile(script);
            RequireFile(Path.Combine(eclipse, "eclipse.exe"));
            RequireFile(Path.Combine(java, "bin", "javac.exe"));
            if (!Directory.Exists(workspace)) throw new DirectoryNotFoundException(workspace);
            Console.WriteLine("Repositorio: " + repository);
            Console.WriteLine("Eclipse: " + eclipse);
            Console.WriteLine("Workspace: " + workspace);
            if (checkOnly)
            {
                Console.WriteLine("Configuración válida. No se ha iniciado el despliegue.");
                return 0;
            }
            Console.WriteLine("\nCompilando la actualización. Cuando se indique, cierra Eclipse normalmente.");
            Console.WriteLine("El instalador actualizará JavaFXCompanion y volverá a abrir el workspace.\n");
            string powershell = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System),
                "WindowsPowerShell", "v1.0", "powershell.exe");
            var start = new ProcessStartInfo(powershell,
                "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File " + Quote(script)
                + " -WaitForExit -Restart -Workspace " + Quote(workspace)
                + " -EclipseHome " + Quote(eclipse) + " -JavaHome " + Quote(java));
            start.UseShellExecute = false;
            start.WorkingDirectory = repository;
            int exitCode;
            using (var process = Process.Start(start))
            {
                process.WaitForExit();
                exitCode = process.ExitCode;
            }
            Console.WriteLine(exitCode == 0 ? "\nActualización completada." : "\nLa actualización ha fallado. Revisa el error anterior.");
            Pause();
            return exitCode;
        }
        catch (Exception error)
        {
            Console.Error.WriteLine("Error: " + error.Message);
            if (!checkOnly) Pause();
            return 1;
        }
    }

    private static void RequireFile(string path)
    {
        if (!File.Exists(path)) throw new FileNotFoundException("No se encuentra " + path);
    }

    private static string Quote(string value)
    {
        if (value.IndexOf('"') >= 0) throw new ArgumentException("Ruta no válida: " + value);
        int trailingSlashes = value.Length - value.TrimEnd('\\').Length;
        return "\"" + value + new string('\\', trailingSlashes) + "\"";
    }

    private static void Pause()
    {
        if (Console.IsInputRedirected) return;
        Console.WriteLine("Pulsa una tecla para cerrar.");
        Console.ReadKey(true);
    }
}
