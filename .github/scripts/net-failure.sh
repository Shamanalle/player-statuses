# Sourced by net-retry.sh and server-smoke.sh: tells a failed download from every other failure.
#
#   network_failure <log>   true when the log shows a download that failed on the way (a timeout, a reset
#                           or refused connection, an unknown host, a TLS handshake cut off, HTTP 408 / 429
#                           / 5xx), false for everything else, a missing file (404) included
#
# A file that is not there is not a network failure: trying again finds it missing again, and a wrong
# version in gradle.properties must fail the job, not be tried twice.

net_failure_pattern='(Received status code (408|429|5[0-9][0-9])|Server returned HTTP response code: (408|429|5[0-9][0-9])|status code: (408|429|5[0-9][0-9])|The requested URL returned error: (408|429|5[0-9][0-9])|Read timed out|[Cc]onnect timed out|Connection timed out|[Cc]onnection reset|Connection refused|Remote host terminated the handshake|[Uu]nknownHostException|Could not resolve host|Temporary failure in name resolution|No route to host|Network is unreachable|SSLHandshakeException|Premature end of Content-Length|Got socket exception|SocketTimeoutException|SocketException|Operation timed out|transfer closed with|These libraries failed to download)'

network_failure() {
  [ -f "$1" ] && grep -Eq "${net_failure_pattern}" "$1"
}
