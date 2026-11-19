# Arranque del clúster: capa Jetour / recuperación espontánea (2026-11-19)

## Observación de prueba en el vehículo
- En varios encendidos recientes, tras iniciar la T1 aparece la capa Jetour por delante de los mapas del clúster.
- En ese estado la rueda del volante no opera sobre los mapas.
- Más tarde, durante el uso normal, el problema se recupera por sí mismo **sin matar procesos manualmente**.
- Una acción correlacionada por el conductor es pedir navegar desde DriveLab Uber Beta, que vuelve a enviar mapas. No está confirmado que ese sea el evento exacto responsable.

## Hipótesis, no conclusiones
- Desfase en el orden de superficies/overlays o en la reactivación del foco entre HOME de T1 Launcher y la vista del clúster.
- Recuperación al volver a lanzar la tarea de mapas, al traerla al frente o al reenviar el destino.
- Una condición de inicialización, más que saturación obligatoria de RAM.

## Diagnóstico requerido, sin modificar componentes OEM
1. Guardar el instante del boot completo y el estado del clúster cuando la capa Jetour tapa el mapa.
2. Capturar únicamente metadatos de pantalla/tarea: Display 1, nombres de tareas de mapas, estado del puente y última tecla de volante, sin acciones de reinicio.
3. Comparar con el primer momento en que Uber vuelve a enviar un destino o cuando se recupere solo.
4. Registrar qué evento coincide realmente con la recuperación. No atribuirla a Uber hasta tener evidencia.
5. Si el evento es repetible, ofrecer recuperación idempotente dirigida a los mapas, sin matar procesos ni alterar HOME/Jetour/seguridad.

**No habilitar reparación automática de clúster ni agregar modificaciones OEM por esta observación aislada.** El trabajo responsive del Cockpit es independiente.
