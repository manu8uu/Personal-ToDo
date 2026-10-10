package com.mentor.app;

import com.jayway.jsonpath.JsonPath;
import com.mentor.app.config.ApiKeyFilter;
import com.mentor.app.service.RecurrenceService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.api-key=clave-de-test")
class ApiIntegrationTest {

    static final String API_KEY = "clave-de-test";

    // una sola base de datos para toda la clase; Ryuk la elimina al terminar
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

       @Autowired RecurrenceService recurrence;
    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired WebApplicationContext context;
    @Autowired ApiKeyFilter apiKeyFilter;
    @Autowired JdbcTemplate jdbc;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(apiKeyFilter).build();
        jdbc.execute("TRUNCATE progress_entry, task_occurrence, task, goal RESTART IDENTITY CASCADE");
    }

    // ---------- seguridad ----------

    @Test
    @DisplayName("sin API key devuelve 401")
    void sinApiKey() throws Exception {
        mvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("con API key incorrecta devuelve 401")
    void apiKeyIncorrecta() throws Exception {
        mvc.perform(get("/api/tasks").header("X-API-Key", "otra"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("con API key correcta devuelve 200")
    void apiKeyCorrecta() throws Exception {
        doGet("/api/tasks").andExpect(status().isOk());
    }

    // ---------- tareas y ocurrencias ----------

    @Test
    @DisplayName("una tarea ocasional genera su ocurrencia de hoy")
    void tareaOcasionalGeneraOcurrencia() throws Exception {
        long taskId = createTask(taskJson("Estudiar ingles", "h", "2", null));

        doGet("/api/occurrences")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].taskId").value((int) taskId))
                .andExpect(jsonPath("$[0].taskTitle").value("Estudiar ingles"))
                .andExpect(jsonPath("$[0].unit").value("h"))
                .andExpect(jsonPath("$[0].progress").value(0.0))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("un progreso parcial actualiza la barra")
    void progresoParcial() throws Exception {
        long occId = occurrenceIdOf(createTask(taskJson("Estudiar ingles", "h", "2", null)));

        addProgress(occId, "1.5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentValue").value(1.5))
                .andExpect(jsonPath("$.progress").value(0.75))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("al llegar a la meta la ocurrencia pasa a DONE")
    void completarMarcaDone() throws Exception {
        long occId = occurrenceIdOf(createTask(taskJson("Escribir", "palabras", "1000", null)));

        addProgress(occId, "600").andExpect(jsonPath("$.status").value("PENDING"));
        addProgress(occId, "400")
                .andExpect(jsonPath("$.progress").value(1.0))
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    @DisplayName("un progreso negativo corrige el valor")
    void correccionNegativa() throws Exception {
        long occId = occurrenceIdOf(createTask(taskJson("Estudiar ingles", "h", "2", null)));

        addProgress(occId, "1.5");
        addProgress(occId, "-0.5")
                .andExpect(jsonPath("$.currentValue").value(1.0))
                .andExpect(jsonPath("$.progress").value(0.5));
    }

    @Test
    @DisplayName("un progreso de 0 se rechaza")
    void progresoCero() throws Exception {
        long occId = occurrenceIdOf(createTask(taskJson("Estudiar ingles", "h", "2", null)));

        addProgress(occId, "0").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("progreso sobre una ocurrencia inexistente devuelve 404")
    void ocurrenciaInexistente() throws Exception {
        addProgress(9999, "1").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("las ocurrencias se filtran por rango de fechas")
    void rangoDeFechas() throws Exception {
        LocalDate dentroDeTres = LocalDate.now().plusDays(3);
        createTask(taskJson("Futura", "h", "1", "\"startDate\":\"" + dentroDeTres + "\""));

        doGet("/api/occurrences").andExpect(jsonPath("$.length()").value(0));
        doGet("/api/occurrences?from=" + LocalDate.now() + "&to=" + LocalDate.now().plusDays(5))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value(dentroDeTres.toString()));
    }

    @Test
    @DisplayName("un rango con 'to' anterior a 'from' se rechaza")
    void rangoInvalido() throws Exception {
        doGet("/api/occurrences?from=2026-10-10&to=2026-10-01")
                .andExpect(status().isBadRequest());
    }

    // ---------- validaciones ----------

    @Test
    @DisplayName("una tarea sin título se rechaza")
    void tituloVacio() throws Exception {
        doPost("/api/tasks", taskJson("   ", "h", "2", null)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("una meta de 0 se rechaza")
    void metaCero() throws Exception {
        doPost("/api/tasks", taskJson("Algo", "h", "0", null)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("endDate anterior a startDate se rechaza")
    void fechaFinAnterior() throws Exception {
        String extra = "\"startDate\":\"2026-10-10\",\"endDate\":\"2026-10-01\"";
        doPost("/api/tasks", taskJson("Algo", "h", "1", extra)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("una tarea con un objetivo inexistente se rechaza")
    void objetivoInexistente() throws Exception {
        doPost("/api/tasks", taskJson("Algo", "h", "1", "\"goalId\":9999"))
                .andExpect(status().isBadRequest());
    }

    // ---------- borrados ----------

    @Test
    @DisplayName("borrar una tarea borra sus ocurrencias y su historial")
    void borrarTareaBorraHistorial() throws Exception {
        long taskId = createTask(taskJson("Estudiar ingles", "h", "2", null));
        addProgress(occurrenceIdOf(taskId), "1");

        doDelete("/api/tasks/" + taskId).andExpect(status().isNoContent());

        assertThat(count("task_occurrence")).isZero();
        assertThat(count("progress_entry")).isZero();
    }

    @Test
    @DisplayName("borrar un objetivo deja sus tareas sin objetivo")
    void borrarObjetivoDejaTarea() throws Exception {
        long goalId = createGoal("Aprobar el B2");
        long taskId = createTask(taskJson("Estudiar ingles", "h", "2", "\"goalId\":" + goalId));

        doDelete("/api/goals/" + goalId).andExpect(status().isNoContent());

        Long goalDeLaTarea = jdbc.queryForObject("SELECT goal_id FROM task WHERE id = ?", Long.class, taskId);
        assertThat(goalDeLaTarea).isNull();
    }

    // ---------- objetivos ----------

    @Test
    @DisplayName("CRUD básico de objetivos")
    void crudObjetivos() throws Exception {
        long id = createGoal("Aprobar el B2");

        doGet("/api/goals")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Aprobar el B2"));

        mvc.perform(put("/api/goals/" + id)
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Aprobar el C1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Aprobar el C1"));

        doDelete("/api/goals/" + id).andExpect(status().isNoContent());
        doDelete("/api/goals/" + id).andExpect(status().isNotFound());
    }

    // ---------- integridad en la base de datos ----------

    @Test
    @DisplayName("current_value coincide con la suma de las entradas de progreso")
    void sumaCoincideConValorActual() throws Exception {
        long occId = occurrenceIdOf(createTask(taskJson("Estudiar ingles", "h", "5", null)));

        addProgress(occId, "1");
        addProgress(occId, "0.75");
        addProgress(occId, "-0.25");

        BigDecimal suma = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM progress_entry WHERE occurrence_id = ?",
                BigDecimal.class, occId);
        BigDecimal actual = jdbc.queryForObject(
                "SELECT current_value FROM task_occurrence WHERE id = ?", BigDecimal.class, occId);

        assertThat(suma).isEqualByComparingTo(actual);
        assertThat(actual).isEqualByComparingTo("1.5");
    }

    @Test
    @DisplayName("la base de datos rechaza un estado inválido aunque se salten la API")
    void baseDeDatosRechazaEstadoInvalido() throws Exception {
        long taskId = createTask(taskJson("Estudiar ingles", "h", "2", null));

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO task_occurrence (task_id, occurrence_date, target_value, status) "
                        + "VALUES (?, ?, 1, 'HECHO')",
                taskId, LocalDate.now().plusDays(1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

        // ---------- eliminar tareas recurrentes ----------

    private static final String DIARIA = taskJson("Leer", "paginas", "20", "\"recurrenceRule\":\"FREQ=DAILY\"");

    @Test
    @DisplayName("omitir un día de una tarea diaria lo oculta y no se regenera")
    void omitirDia() throws Exception {
        long taskId = createTask(DIARIA);

        doDelete("/api/occurrences/" + todayOccurrenceId(taskId)).andExpect(status().isNoContent());

        doGet("/api/occurrences").andExpect(jsonPath("$.length()").value(0));
        recurrence.refresh();
        doGet("/api/occurrences").andExpect(jsonPath("$.length()").value(0));
        // la fila omitida sigue ahí, sin duplicados
        assertThat(count("task_occurrence")).isEqualTo(29);
    }

    @Test
    @DisplayName("omitir un día con progreso borra ese progreso")
    void omitirDiaBorraProgreso() throws Exception {
        long occId = todayOccurrenceId(createTask(DIARIA));
        addProgress(occId, "5");

        doDelete("/api/occurrences/" + occId).andExpect(status().isNoContent());

        assertThat(count("progress_entry")).isZero();
    }

    @Test
    @DisplayName("no se puede registrar progreso en un día omitido")
    void progresoEnDiaOmitido() throws Exception {
        long occId = todayOccurrenceId(createTask(DIARIA));
        doDelete("/api/occurrences/" + occId).andExpect(status().isNoContent());

        addProgress(occId, "1").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("omitir la única ocurrencia de una tarea ocasional borra la tarea")
    void omitirOcasionalBorraTarea() throws Exception {
        long taskId = createTask(taskJson("Una vez", "h", "1", null));

        doDelete("/api/occurrences/" + occurrenceIdOf(taskId)).andExpect(status().isNoContent());

        assertThat(count("task")).isZero();
    }

    @Test
    @DisplayName("eliminar una serie desde hoy conserva el historial anterior")
    void eliminarSerieConservaHistorial() throws Exception {
        LocalDate hoy = LocalDate.now();
        long taskId = createTask(taskJson("Leer", "paginas", "20",
                "\"recurrenceRule\":\"FREQ=DAILY\",\"startDate\":\"" + hoy.minusDays(3) + "\""));
        for (int d = 2; d >= 1; d--) {
            jdbc.update("INSERT INTO task_occurrence "
                    + "(task_id, occurrence_date, target_value, current_value, status) "
                    + "VALUES (?, ?, 20, 20, 'DONE')", taskId, hoy.minusDays(d));
        }

        doDelete("/api/tasks/" + taskId + "?scope=future").andExpect(status().isNoContent());

        assertThat(count("task_occurrence")).isEqualTo(2);
        doGet("/api/occurrences").andExpect(jsonPath("$.length()").value(0));
        String fin = jdbc.queryForObject(
                "SELECT to_char(end_date, 'YYYY-MM-DD') FROM task WHERE id = ?", String.class, taskId);
        assertThat(fin).isEqualTo(hoy.minusDays(1).toString());

        recurrence.refresh();
        assertThat(count("task_occurrence")).isEqualTo(2);
    }

    @Test
    @DisplayName("eliminar una serie sin historial borra la tarea entera")
    void eliminarSerieSinHistorial() throws Exception {
        long taskId = createTask(DIARIA);

        doDelete("/api/tasks/" + taskId + "?scope=future").andExpect(status().isNoContent());

        assertThat(count("task")).isZero();
        assertThat(count("task_occurrence")).isZero();
    }

    @Test
    @DisplayName("un alcance de borrado desconocido se rechaza")
    void alcanceInvalido() throws Exception {
        long taskId = createTask(DIARIA);

        doDelete("/api/tasks/" + taskId + "?scope=otro").andExpect(status().isBadRequest());
    }

    // ---------- utilidades ----------

    private ResultActions doGet(String url) throws Exception {
        return mvc.perform(get(url).header("X-API-Key", API_KEY));
    }

    private ResultActions doPost(String url, String body) throws Exception {
        return mvc.perform(post(url)
                .header("X-API-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions doDelete(String url) throws Exception {
        return mvc.perform(delete(url).header("X-API-Key", API_KEY));
    }

    private ResultActions addProgress(long occurrenceId, String amount) throws Exception {
        return doPost("/api/occurrences/" + occurrenceId + "/progress", "{\"amount\":" + amount + "}");
    }

    // extra es un fragmento JSON opcional, por ejemplo "goalId":3
    private static String taskJson(String title, String unit, String target, String extra) {
        return "{\"title\":\"%s\",\"unit\":\"%s\",\"targetValue\":%s%s}"
                .formatted(title, unit, target, extra == null ? "" : "," + extra);
    }

    private long createTask(String json) throws Exception {
        MvcResult result = doPost("/api/tasks", json).andExpect(status().isCreated()).andReturn();
        return idOf(result);
    }

    private long createGoal(String title) throws Exception {
        MvcResult result = doPost("/api/goals", "{\"title\":\"" + title + "\"}")
                .andExpect(status().isCreated()).andReturn();
        return idOf(result);
    }

    private long occurrenceIdOf(long taskId) {
        Long id = jdbc.queryForObject("SELECT id FROM task_occurrence WHERE task_id = ?", Long.class, taskId);
        assertThat(id).isNotNull();
        return id;
    }
        private long todayOccurrenceId(long taskId) {
        Long id = jdbc.queryForObject(
                "SELECT id FROM task_occurrence WHERE task_id = ? AND occurrence_date = ?",
                Long.class, taskId, LocalDate.now());
        assertThat(id).isNotNull();
        return id;
    }

    private long count(String table) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
        return n == null ? 0 : n;
    }

    private static long idOf(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }
        @Test
    @DisplayName("una tarea diaria genera las ocurrencias de las próximas 4 semanas")
    void tareaDiariaGeneraHorizonte() throws Exception {
        createTask(taskJson("Leer", "paginas", "20", "\"recurrenceRule\":\"FREQ=DAILY\""));

        // hoy y los 28 días siguientes
        assertThat(count("task_occurrence")).isEqualTo(29);
        doGet("/api/occurrences")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].recurrenceRule").value("FREQ=DAILY"));
    }

    @Test
    @DisplayName("una tarea semanal solo genera los días indicados")
    void tareaSemanal() throws Exception {
        String hoy = LocalDate.now().getDayOfWeek().name().substring(0, 2);
        createTask(taskJson("Correr", "km", "5",
                "\"recurrenceRule\":\"FREQ=WEEKLY;BYDAY=" + hoy + "\""));

        // el mismo día de la semana cae 5 veces en 29 días
        assertThat(count("task_occurrence")).isEqualTo(5);
    }

    @Test
    @DisplayName("una repetición no soportada se rechaza")
    void repeticionInvalida() throws Exception {
        doPost("/api/tasks", taskJson("Algo", "h", "1", "\"recurrenceRule\":\"FREQ=MONTHLY\""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("refrescar las recurrencias no duplica ocurrencias")
    void refrescarEsIdempotente() throws Exception {
        createTask(taskJson("Leer", "paginas", "20", "\"recurrenceRule\":\"FREQ=DAILY\""));

        recurrence.refresh();
        recurrence.refresh();

        assertThat(count("task_occurrence")).isEqualTo(29);
    }

    @Test
    @DisplayName("las pendientes de días pasados se marcan como falladas")
    void pendientesPasadasSeMarcan() throws Exception {
        long taskId = createTask(taskJson("Ayer", "h", "1",
                "\"startDate\":\"" + LocalDate.now().minusDays(1) + "\""));

        recurrence.refresh();

        String status = jdbc.queryForObject(
                "SELECT status FROM task_occurrence WHERE task_id = ?", String.class, taskId);
        assertThat(status).isEqualTo("MISSED");
    }

        private long pendingTaskOn(LocalDate date) throws Exception {
        return createTask(taskJson("Tarea", "h", "1", "\"startDate\":\"" + date + "\""));
    }

    private long completedTaskOn(LocalDate date) throws Exception {
        long taskId = pendingTaskOn(date);
        addProgress(occurrenceIdOf(taskId), "1").andExpect(status().isOk());
        return taskId;
    }
        // ---------- estadísticas ----------

    @Test
    @DisplayName("sin datos, las estadísticas están a cero")
    void estadisticasVacias() throws Exception {
        doGet("/api/stats")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(0))
                .andExpect(jsonPath("$.bestStreak").value(0))
                .andExpect(jsonPath("$.totalCompleted").value(0))
                .andExpect(jsonPath("$.days.length()").value(0));
    }

    @Test
    @DisplayName("la racha cuenta días consecutivos con todo completado")
    void rachaConsecutiva() throws Exception {
        LocalDate hoy = LocalDate.now();
        completedTaskOn(hoy.minusDays(3));
        completedTaskOn(hoy.minusDays(2));
        completedTaskOn(hoy.minusDays(1));

        doGet("/api/stats")
                .andExpect(jsonPath("$.currentStreak").value(3))
                .andExpect(jsonPath("$.bestStreak").value(3))
                .andExpect(jsonPath("$.totalCompleted").value(3));
    }

    @Test
    @DisplayName("un día fallado rompe la racha, pero la mejor se conserva")
    void rachaRota() throws Exception {
        LocalDate hoy = LocalDate.now();
        completedTaskOn(hoy.minusDays(4));
        completedTaskOn(hoy.minusDays(3));
        pendingTaskOn(hoy.minusDays(2));
        completedTaskOn(hoy.minusDays(1));

        doGet("/api/stats")
                .andExpect(jsonPath("$.currentStreak").value(1))
                .andExpect(jsonPath("$.bestStreak").value(2));
    }

    @Test
    @DisplayName("hoy pendiente no rompe la racha y al completarlo suma")
    void hoyPendiente() throws Exception {
        LocalDate hoy = LocalDate.now();
        completedTaskOn(hoy.minusDays(2));
        completedTaskOn(hoy.minusDays(1));
        long taskId = pendingTaskOn(hoy);

        doGet("/api/stats").andExpect(jsonPath("$.currentStreak").value(2));

        addProgress(occurrenceIdOf(taskId), "1").andExpect(status().isOk());

        doGet("/api/stats").andExpect(jsonPath("$.currentStreak").value(3));
    }

    @Test
    @DisplayName("un día sin tareas programadas no rompe la racha")
    void diaSinTareas() throws Exception {
        LocalDate hoy = LocalDate.now();
        completedTaskOn(hoy.minusDays(3));
        completedTaskOn(hoy.minusDays(1));

        doGet("/api/stats").andExpect(jsonPath("$.currentStreak").value(2));
    }

    @Test
    @DisplayName("el cumplimiento semanal cuenta las tareas de esta semana")
    void cumplimientoSemanal() throws Exception {
        completedTaskOn(LocalDate.now());
        pendingTaskOn(LocalDate.now());

        doGet("/api/stats")
                .andExpect(jsonPath("$.weekDone").value(1))
                .andExpect(jsonPath("$.weekTotal").value(2));
    }

    @Test
    @DisplayName("la ventana del mapa de actividad empieza en lunes")
    void ventanaEmpiezaEnLunes() throws Exception {
        LocalDate esperado = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(16); // 17 semanas por defecto

        doGet("/api/stats").andExpect(jsonPath("$.from").value(esperado.toString()));
    }
}