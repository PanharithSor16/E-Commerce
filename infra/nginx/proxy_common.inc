# Shared proxy settings. X-Forwarded-For is overwritten (not appended) with the real client
# address, so clients cannot inject a fake IP; the backend trusts it only from this proxy.
proxy_http_version 1.1;
proxy_set_header Host              $host;
proxy_set_header X-Real-IP         $remote_addr;
proxy_set_header X-Forwarded-For   $remote_addr;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-Host  $host;
proxy_set_header Upgrade           $http_upgrade;
proxy_set_header Connection        $connection_upgrade;
proxy_read_timeout 60s;

gzip on;
gzip_types text/plain text/css application/json application/javascript image/svg+xml;
