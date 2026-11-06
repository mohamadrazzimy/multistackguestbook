FROM php:8.3-apache
COPY index.php index.html /var/www/html/
RUN mkdir -p /data && chown www-data:www-data /data \
    && printf 'FallbackResource /index.php\n' > /var/www/html/.htaccess \
    && printf '<Directory /var/www/html>\nAllowOverride All\n</Directory>\n' \
       > /etc/apache2/conf-available/guestbook.conf \
    && a2enconf guestbook
EXPOSE 80
