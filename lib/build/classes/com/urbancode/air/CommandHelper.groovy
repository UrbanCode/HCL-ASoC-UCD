/*
* Licensed Materials - Property of IBM* and/or HCL**
* UrbanCode Deploy
* UrbanCode Build
* UrbanCode Release
* AnthillPro
* (c) Copyright IBM Corporation 2011, 2017. All Rights Reserved.
* (c) Copyright HCL Technologies Ltd. 2018. All Rights Reserved.
*
* U.S. Government Users Restricted Rights - Use, duplication or disclosure restricted by
* GSA ADP Schedule Contract with IBM Corp.
*
* * Trademark of International Business Machines
* ** Trademark of HCL Technologies Limited
*/
package com.urbancode.air

import java.util.regex.Matcher

public class CommandHelper {

    //**************************************************************************
    // CLASS
    //**************************************************************************

    /**
     * This is the set of characters which would represent special processing to a shell interpreter either breaking
     * a word or being evaluated as non-literal text within a word.
     *
     *<pre>
     * metacharacter
     *        A character that, when unquoted, separates words.  One of the following:
     *        |  &amp; ; ( ) &lt; &gt; space tab
     *
     * QUOTING
     *
     * Quoting  is  used to remove the special meaning of certain characters or words to the shell.  Quoting
     * can be used to disable special treatment for special characters, to prevent reserved words from being
     * recognized as such, and to prevent parameter expansion.
     *
     * Each  of  the metacharacters listed above has special meaning to the shell and must
     * be quoted if it is to represent itself.
     *
     * When the command history expansion facilities are being used (see HISTORY EXPANSION below), the  his-
     * tory expansion character, usually !, must be quoted to prevent history expansion.
     *
     * There are three quoting mechanisms: the escape character, single quotes, and double quotes.
     *
     * A non-quoted backslash (\) is the escape character.  It preserves the literal value of the next char-
     * acter that follows, with the exception of &lt;newline&gt;.  If a \&lt;newline&gt; pair appears, and the backslash
     * is  not  itself quoted, the \&lt;newline&gt; is treated as a line continuation (that is, it is removed from
     * the input stream and effectively ignored).
     *
     * Enclosing characters in single quotes preserves the  literal  value  of  each  character  within  the
     * quotes.  A single quote may not occur between single quotes, even when preceded by a backslash.
     *
     * Enclosing  characters  in  double  quotes  preserves  the  literal value of all characters within the
     * quotes, with the exception of $, `, \, and, when history expansion is enabled, !.  The  characters  $
     * and  `  retain their special meaning within double quotes.  The backslash retains its special meaning
     * only when followed by one of the following characters: $, `, ", \, or &lt;newline&gt;.  A double quote  may
     * be  quoted within double quotes by preceding it with a backslash.  If enabled, history expansion will
     * be performed unless an !  appearing in double quotes is escaped using  a  backslash.   The  backslash
     * preceding the !  is not removed.
     *
     * The special parameters * and @ have special meaning when in double quotes (see PARAMETERS below).
     *
     * Words  of  the  form  $'string'  are  treated specially.  The word expands to string, with backslash-
     * escaped characters replaced as specified by the ANSI C  standard.   Backslash  escape  sequences,  if
     * present, are decoded as follows:
     *        \a     alert (bell)
     *        \b     backspace
     *        \e     an escape character
     *        \f     form feed
     *        \n     new line
     *        \r     carriage return
     *        \t     horizontal tab
     *        \v     vertical tab
     *        \\     backslash
     *        \'     single quote
     *        \nnn   the eight-bit character whose value is the octal value nnn (one to three digits)
     *        \xHH   the eight-bit character whose value is the hexadecimal value HH (one or two hex digits)
     *        \cx    a control-x character
     *
     * The expanded result is single-quoted, as if the dollar sign had not been present.
     *
     * A double-quoted string preceded by a dollar sign ($) will cause the string to be translated according
     * to  the  current  locale.   If  the current locale is C or POSIX, the dollar sign is ignored.  If the
     * string is translated and replaced, the replacement is double-quoted.
     * </pre>
     */

    static private final Collection<String> specialChars;
    static {
        Set<String> chars = new LinkedHashSet<String>();
        Collections.addAll(chars, "|&;()<> \t\n".split("")); // word breaking chars, see meta-character section
        Collections.addAll(chars, "{}"          .split("")); // compound command chars
        Collections.addAll(chars, "'\"\\"       .split("")); // quoting chars
        Collections.addAll(chars, "\$[]*!"      .split("")); // expanding chars
        Collections.addAll(chars,  "`"          .split("")); // sub-command chars
        chars.remove(""); // ensure empty-string is not present
        specialChars = Collections.unmodifiableCollection(chars);
    }

    //**************************************************************************
    // INSTANCE
    //**************************************************************************

    private final def pb
    private def out = System.out
    boolean ignoreExitValue = false

    public CommandHelper(dir) {
        pb = new ProcessBuilder(['echo'] as String[]).directory(dir)
    }

    public Map<String,String> environment() {
        return pb.environment();
    }

    /**
     * A convenience method for running commands and optionally parsing the stdout of that command.
     * The process' stdOut and stdErr are forwarded to this scripts 'stdOut' and stdIn is untouched.
     *
     * @param message an optional message to print prior to the commandline
     * @param command the command to be used as a String[]
     * @see #runCommand(def,def,Closure)
     */
    public int runCommand(def message, def command) {
        return runCommand(message, command, null, null)
    }
    /**
     * A convenience method for running commands and optionally parsing the stdout of that command.
     * An input String can be provided and will be written to the process OutputStream
     * The process' stdOut and stdErr are forwarded to this scripts 'stdOut' and stdIn is untouched.
     *
     * @param message an optional message to print prior to the commandline
     * @param command the command to be used as a String[]
     * @param input String that will be written to the process OutputStream
     * @see #runCommand(def,def,Closure)
     */
    public int runCommand(def message, def command, String input) {
        return runCommand(message, command, null, input)
    }
    /**
     * A convenience method for running commands and optionally parsing the stdout of that command.
     * If closure is non-null, the closure will be passed the resultant {@link Process} and is expected to deal with all IO.
     * The process' stdOut and stdErr are forwarded to this scripts 'stdOut' and stdIn is untouched.
     *
     * @param message an optional message to print prior to the commandline
     * @param command the command to be used as a String[]
     * @param closure an optional closure to deal with Process IO
     * @see #runCommand(def,def,Closure)
     */
    public int runCommand(def message, def command, Closure closure) {
        return runCommand(message, command, closure, null)
    }

    /**
     * A convenience method for running commands and optionally parsing the stdout of that command.
     * If closure is non-null, the closure will be passed the resultant {@link Process} and is expected to deal with all IO.
     * An input String can be provided and will be written to the process OutputStream
     * Otherwise, the process' stdOut and stdErr are forwarded to this scripts 'stdOut' and stdIn is untouched.
     *
     * @param message an optional message to print prior to the commandline
     * @param command the command to be used as a String[]
     * @param closure an optional closure to deal with Process IO
     * @param input String that will be written to the process OutputStream
     */
    public int runCommand(def message, def command, Closure closure, String input) {
        command[0] = sanitizeExecutable(command[0])
        pb.command(command as String[])
        println()
        if (message) {
            println(message)
        }

        Thread hook = null;

        println("command: ${pb.command().collect{addDisplayQuotes(it)}.join(' ')}")
        Process proc = pb.start()
        try {
            // need to use shutdown hooks in order to cascade sig-int (ctrl-C) to child proc
            hook = { proc.destroy();} as Thread;
            addShutdownHook(hook);

            if (input != null && input.length() > 0) {
                proc.getOutputStream().write(input.getBytes());
            }
            if (closure) {
                closure(proc)
                proc.waitFor()
            }
            else {
                proc.out.close() // close stdin
                def out = new PrintStream(this.out, true)
                try {
                    // forward stdout and stderr to autoflushing output stream
                    proc.waitForProcessOutput(out, out)
                    proc.waitFor()
                }
                finally {
                    out.flush();
                }
            }
        }
        finally {
            // best effort to kill the child process if this process is aborted
            proc.destroy();
            if (hook != null) {
                // cleanup non-shutdown scenarios
                removeShutdownHook(hook);
            }
        }
        if (!ignoreExitValue && proc.exitValue()) {
            throw new ExitCodeException("Command failed with exit code: " + proc.exitValue())
        }
        return proc.exitValue();
    }

    /**
     * Overloaded method that accepts a map of named arguments. Currently supported arguments are:
     *    - (required) command - List<String>
     *    - (optional) message - String
     *    - (optional) closure - Closure
     *    - (optional) input - String
     *    - (optional) printCommand - List<String>
     * @param args
     * @return
     */
    public int runCommand(Map<String, Object> args) {
        String message = args.message;
        List<String> command = args.command;
        Closure closure = args.closure;
        String input = args.input;
        def printCommand = args.printCommand;
        if (!printCommand) {
            printCommand = command;
        }

        command[0] = sanitizeExecutable(command[0])
        pb.command(command as String[])
        println()
        if (message) {
            println(message)
        }

        Thread hook = null;

        println("command: ${printCommand.collect{addDisplayQuotes(it)}.join(' ')}")
        Process proc = pb.start()
        try {
            // need to use shutdown hooks in order to cascade sig-int (ctrl-C) to child proc
            hook = { proc.destroy();} as Thread;
            addShutdownHook(hook);

            if (input != null && input.length() > 0) {
                proc.getOutputStream().write(input.getBytes());
            }
            if (closure) {
                closure(proc)
                proc.waitFor()
            }
            else {
                proc.out.close() // close stdin
                def out = new PrintStream(this.out, true)
                try {
                    // forward stdout and stderr to autoflushing output stream
                    proc.waitForProcessOutput(out, out)
                    proc.waitFor()
                }
                finally {
                    out.flush();
                }
            }
        }
        finally {
            // best effort to kill the child process if this process is aborted
            proc.destroy();
            if (hook != null) {
                // cleanup non-shutdown scenarios
                removeShutdownHook(hook);
            }
        }
        if (!ignoreExitValue && proc.exitValue()) {
            throw new ExitCodeException("Command failed with exit code: " + proc.exitValue())
        }
        return proc.exitValue();
    }

    public def getProcessBuilder() {
        return pb;
    }

    private void addShutdownHook(Thread hook) {
        Runtime.getRuntime().addShutdownHook(hook);
    }

    private void removeShutdownHook(Thread hook) {
        Runtime.getRuntime().removeShutdownHook(hook);
    }

    /**
     * If the given value contains characters which would be interpreted specially by a shell,
     * applies quoting to the value. Otherwise simply returns the value as is.
     *
     */
    private String addDisplayQuotes(String value) {
        if (requiresDisplayQuotes(value)) {
            // this generates an ugly result for leading/trailing ' characters in original value, could be improved
            return "'"+(value.replaceAll("'", "'\\''"))+"'";
        }
        else {
            return value;
        }
    }

    /**
     * Check if a given argument contains any special shell character which would
     * <ul>
     *   <li>not render (empty argument)</li>
     *   <li>contains any characters which would break word boundaries</li>
     *   <li>contains any expanding/special characters</li>
     * </ul>
     */
    private boolean requiresDisplayQuotes(String value) {
        if (value == null) {
            return false
        }
        // empty string requires quoting to be an explicit argument
        if (value == "" || specialChars.find{value.contains(it)}) {
            return true;
        }
        else {
            return false;
        }
    }

    public void ignoreExitValue(boolean ignore) {
        this.ignoreExitValue = ignore;
    }

    public void addEnvironmentVariable(String key, String value) {
        if (pb != null) {
            Map<String, String> environmentVariables = pb.environment();
            environmentVariables.put(key, value);
        }
    }

    public void removeEnvironmentVariable(String key) {
        if (pb != null) {
            Map<String, String> environmentVariables = pb.environment();
            environmentVariables.remove(key);
        }
    }

    public void printEnvironmentVariables() {
        if (pb != null) {
            Map<String, String> environmentVariables = pb.environment();
            environmentVariables.each { key, value ->
                println "$key=$value"
            }
        }
    }

    private String sanitizeExecutable(String path) {
        String sanitizedPath = path

        File exe = new File(path)
        if (exe.isAbsolute()) {
            sanitizedPath = path.replaceAll("[\\\\/]", Matcher.quoteReplacement(File.separator))
        }

        return sanitizedPath
    }
}
