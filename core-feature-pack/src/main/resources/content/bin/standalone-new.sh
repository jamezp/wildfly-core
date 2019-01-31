#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

# Use --debug to activate debug mode with an optional argument to specify the port.
# Usage : standalone.sh --debug
#         standalone.sh --debug 9797

# By default debug mode is disabled.
readonly DEBUG_MODE="${DEBUG:-'false'}"
readonly DEBUG_PORT="${DEBUG_PORT:-'8787'}"
readonly GC_LOG="${GC_LOG}"

readonly DIRNAME=$(dirname "${0})")
readonly PROGNAME=$(basename "${0}")

# Use the maximum available, or set MAX_FD != -1 to use that
readonly MAX_FD="maximum"

# tell linux glibc how many memory pools can be created that are used by malloc
readonly MALLOC_ARENA_MAX="${MALLOC_ARENA_MAX:'-1'}"
export MALLOC_ARENA_MAX

# OS specific support (must be 'true' or 'false').
case "$(uname)" in
    CYGWIN*)
        readonly cygwin=true
        ;;

    Darwin*)
        readonly darwin=true
        ;;
    FreeBSD)
        readonly freebsd=true
        ;;
    Linux)
        readonly linux=true
        ;;
    SunOS*)
        readonly solaris=true
        ;;
    *)
        readonly other=true
        ;;
esac

# For Cygwin, ensure paths are in UNIX format before anything is touched
if ${cygwin} ; then
    [ -n "${JBOSS_HOME}" ] &&
        JBOSS_HOME=$(cygpath --unix "${JBOSS_HOME}")
    [ -n "${JAVA_HOME}" ] &&
        JAVA_HOME=$(cygpath --unix "${JAVA_HOME}")
    [ -n "${JAVAC_JAR}" ] &&
        JAVAC_JAR=$(cygpath --unix "${JAVAC_JAR}")
fi

# Setup JBOSS_HOME
readonly RESOLVED_JBOSS_HOME=${JBOSS_HOME:-$(cd "$DIRNAME/.." >/dev/null; pwd)}
if [ "x$JBOSS_HOME" = "x" ]; then
    # get the full path (without any relative bits)
    readonly JBOSS_HOME="${RESOLVED_JBOSS_HOME}"
else # this below could and should be handle by the launcher no?
    readonly SANITIZED_JBOSS_HOME=${JBOSS_HOME:-$(cd "$JBOSS_HOME"; pwd)}
 if [ "${RESOLVED_JBOSS_HOME}" != "${SANITIZED_JBOSS_HOME}" ]; then
   echo ""
   echo "   WARNING:  JBOSS_HOME may be pointing to a different installation - unpredictable results may occur."
   echo ""
   echo "             JBOSS_HOME: $JBOSS_HOME"
   echo ""
   sleep 2s
 fi
fi
export JBOSS_HOME

# Setup the JVM
if [ -n "${JAVA}" ]; then
    if [ -n "${JAVA_HOME}" ]; then
        JAVA="${JAVA_HOME}/bin/java"
    else
        JAVA='java'
    fi
fi

# For Cygwin, switch paths to Windows format before running java
if $cygwin; then
    JBOSS_HOME=$(cygpath --path --windows "${JBOSS_HOME}")
    JAVA_HOME=$(cygpath --path --windows "${JAVA_HOME}")
    JBOSS_MODULEPATH=$(cygpath --path --windows "${JBOSS_MODULEPATH}")
    JBOSS_BASE_DIR=$(cygpath --path --windows "${JBOSS_BASE_DIR}")
    JBOSS_LOG_DIR=$(cygpath --path --windows "${JBOSS_LOG_DIR}")
    JBOSS_CONFIG_DIR=$(cygpath --path --windows "${JBOSS_CONFIG_DIR}")
fi

while true; do
   if [ -n "${LAUNCH_JBOSS_IN_BACKGROUND}" ]; then
      # Execute the JVM in the foreground
      eval "${JAVA}" -cp "${JBOSS_HOME}/bin/launcher.jar" 'org.wildfly.core.launcher.StandaloneEntry' $@
      JBOSS_STATUS=${?}
   else
      # Execute the JVM in the background
      eval "${JAVA}" -cp "${JBOSS_HOME}/bin/launcher.jar" org.wildfly.core.launcher.StandaloneEntry $@ \"&\"
      JBOSS_PID="${!}"
      # Trap common signals and relay them to the jboss process
      trap kill -HUP  "${JBOSS_PID}" HUP
      trap kill -TERM "${JBOSS_PID}" INT
      trap kill -QUIT "${JBOSS_PID}" QUIT
      trap kill -PIPE "${JBOSS_PID}" PIPE
      trap kill -TERM "${JBOSS_PID}" TERM
      if [ -n "${JBOSS_PIDFILE}"  ]; then
        echo "${JBOSS_PID}" > "${JBOSS_PIDFILE}"
      fi
      # Wait until the background process exits
      WAIT_STATUS=128
      while [ "$WAIT_STATUS" -ge 128 ]; do
         wait $JBOSS_PID 2>/dev/null
         WAIT_STATUS=$?
         if [ "$WAIT_STATUS" -gt 128 ]; then
            SIGNAL=$(expr "${WAIT_STATUS}" - 128)
            SIGNAL_NAME=$(kill -l "${SIGNAL}")
            echo "*** JBossAS process (${JBOSS_PID}) received $SIGNAL_NAME signal ***" >&2
         fi
      done
      if [ "${WAIT_STATUS}" -lt 127 ]; then
         JBOSS_STATUS=${WAIT_STATUS}
      else
         JBOSS_STATUS=0
      fi
      if [ "${JBOSS_STATUS}" -ne 10 ]; then
            # Wait for a complete shudown
            wait "${JBOSS_PID}" 2>/dev/null
      fi
      if [ -n "${JBOSS_PIDFILE}" ]; then
            grep "${JBOSS_PID}" "${JBOSS_PIDFILE}" && rm "${JBOSS_PIDFILE}"
      fi
   fi
   if [ "${JBOSS_STATUS}" -eq 10 ]; then
      echo "Restarting application server..."
   else
      exit "${JBOSS_STATUS}"
   fi
done
