# Rutina

Rastreador de hábitos, tareas y diario para Android, **100 % local**.

- Sin cuentas, sin nube, sin anuncios.
- La app **no tiene permiso de Internet**: Android le impide enviar tus datos a ningún lado.
- Copia automática de Google desactivada. El respaldo es un archivo que tú exportas desde Ajustes (opcionalmente cifrado con contraseña, AES‑256) y puedes guardar en Drive.

## Instalar
1. Abre la pestaña **Releases** de este repo desde el celular.
2. Descarga el `.apk` más reciente y ábrelo (acepta "instalar apps desconocidas" la primera vez).
3. Las versiones nuevas se instalan encima y conservan tus datos (firma fija guardada como secreto del repo, nunca en el código).

## Estructura
```
app/src/main/java/com/rutina/app/
  data/      Tablas (Room/SQLite) y consultas
  domain/    Reglas: progreso, rachas, sugerencias
  ui/        Pantallas (today, habit, settings, tutorial, ...)
```

## Fases
1. ✅ Base, Hoy, hábitos de Recuento y Dejar, tareas rápidas
2. ✅ Tareas completas, listas y filtros
3. ✅ Diario
4. ✅ Temporizador
5. ✅ Vistas semanal/mensual/anual y estadísticas
6. ✅ Calendario
7. ✅ Recordatorios y widgets
8. ✅ Estado de ánimo, peso y Health Connect
9. ✅ Ajustes, respaldo/restauración (cifrado opcional), logros, archivo de hábitos y tutorial
10. ✅ Tareas avanzadas: entrada inteligente, omitir, repetición personalizada, historial, selección múltiple, agrupar/ordenar, posponer; respaldo automático con contraseña fija
11. Hábitos y widgets avanzados: listas y prioridades de hábitos, reordenar, unidades propias, informes, más widgets
