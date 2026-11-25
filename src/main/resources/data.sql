-- Seed de 20 clientes de ejemplo (datos ficticios).
-- Se ejecuta en cada arranque después de que Hibernate crea las tablas
-- (spring.jpa.defer-datasource-initialization=true), pero solo inserta si la tabla
-- está VACÍA (WHERE NOT EXISTS). Así no consume IDs: un INSERT descartado por
-- conflicto igual avanzaría la secuencia de la columna id en cada arranque.
-- created_at se asigna aquí porque @PrePersist solo corre al guardar desde JPA.
-- days_ago reparte las fechas de alta en los últimos 120 días (now() - days_ago días),
-- así el filtro por fecha de alta (createdFrom / createdTo) devuelve subconjuntos distintos.

INSERT INTO customers (name, last_name, email, phone, address, city, birth_date, active, created_at)
SELECT name, last_name, email, phone, address, city, birth_date::date, active, now() - make_interval(days => days_ago)
FROM (VALUES
    ('Ana',       'Pérez',     'ana.perez@ejemplo.com',        '+56 9 1000 0001', 'Av. Siempre Viva 101',   'Santiago',     '1990-05-20', true, 120),
    ('Bruno',     'González',  'bruno.gonzalez@ejemplo.com',   '+56 9 1000 0002', 'Calle Los Olmos 202',    'Valparaíso',   '1985-11-03', true, 110),
    ('Camila',    'Rojas',     'camila.rojas@ejemplo.com',     '+56 9 1000 0003', 'Pasaje Las Rosas 303',   'Concepción',   '1993-02-14', true, 100),
    ('Diego',     'Muñoz',     'diego.munoz@ejemplo.com',      '+56 9 1000 0004', 'Av. Central 404',        'La Serena',    '1988-07-09', true,  90),
    ('Elena',     'Díaz',      'elena.diaz@ejemplo.com',       '+56 9 1000 0005', 'Calle del Sol 505',      'Antofagasta',  '1979-12-25', true,  80),
    ('Felipe',    'Soto',      'felipe.soto@ejemplo.com',      '+56 9 1000 0006', 'Av. Libertad 606',       'Temuco',       '1995-04-30', true,  70),
    ('Gabriela',  'Contreras', 'gabriela.contreras@ejemplo.com','+56 9 1000 0007', 'Calle Norte 707',       'Rancagua',     '1991-09-17', true,  62),
    ('Héctor',    'Silva',     'hector.silva@ejemplo.com',     '+56 9 1000 0008', 'Pasaje Sur 808',         'Talca',        '1982-03-08', false,  55),
    ('Isabel',    'Martínez',  'isabel.martinez@ejemplo.com',  '+56 9 1000 0009', 'Av. Los Pinos 909',      'Arica',        '1997-06-21', true,  48),
    ('Javier',    'Sepúlveda', 'javier.sepulveda@ejemplo.com', '+56 9 1000 0010', 'Calle Oriente 110',      'Iquique',      '1987-10-12', true,  41),
    ('Karina',    'Morales',   'karina.morales@ejemplo.com',   '+56 9 1000 0011', 'Av. Poniente 211',       'Puerto Montt', '1994-01-05', true,  35),
    ('Luis',      'Rodríguez', 'luis.rodriguez@ejemplo.com',   '+56 9 1000 0012', 'Calle Las Acacias 312',  'Chillán',      '1980-08-28', true,  29),
    ('María',     'López',     'maria.lopez@ejemplo.com',      '+56 9 1000 0013', 'Pasaje El Bosque 413',   'Valdivia',     '1999-11-11', true,  23),
    ('Nicolás',   'Fuentes',   'nicolas.fuentes@ejemplo.com',  '+56 9 1000 0014', 'Av. Del Mar 514',        'Viña del Mar', '1986-05-02', false,  18),
    ('Olivia',    'Hernández', 'olivia.hernandez@ejemplo.com', '+56 9 1000 0015', 'Calle Mirador 615',      'Osorno',       '1992-07-19', true,  14),
    ('Pablo',     'Torres',    'pablo.torres@ejemplo.com',     '+56 9 1000 0016', 'Av. Cordillera 716',     'Calama',       '1983-02-27', true,  10),
    ('Valentina', 'Araya',     'valentina.araya@ejemplo.com',  '+56 9 1000 0017', 'Calle Los Aromos 817',   'Copiapó',      '1996-12-01', true,   7),
    ('Ricardo',   'Flores',    'ricardo.flores@ejemplo.com',   '+56 9 1000 0018', 'Pasaje Las Lilas 918',   'Punta Arenas', '1978-04-16', true,   4),
    ('Sofía',     'Espinoza',  'sofia.espinoza@ejemplo.com',   '+56 9 1000 0019', 'Av. Las Torres 119',     'Los Ángeles',  '1998-09-23', true,   2),
    ('Tomás',     'Valenzuela','tomas.valenzuela@ejemplo.com', '+56 9 1000 0020', 'Calle Esperanza 220',    'Quilpué',      '1989-06-06', true,   0)
) AS seed (name, last_name, email, phone, address, city, birth_date, active, days_ago)
WHERE NOT EXISTS (SELECT 1 FROM customers);

-- Facturas de ejemplo (datos ficticios), solo si la tabla invoices está VACÍA: en Supabase,
-- que ya tiene facturas, no inserta nada. El cliente se busca por email (los ids dependen de la
-- base) y los importes son coherentes con las líneas (IVA del 19 %). created_at a mano porque
-- @PrePersist no corre en SQL directo.
INSERT INTO invoices (invoice_number, customer_id, issue_date, due_date, subtotal, tax, total, status, notes, created_at)
SELECT s.invoice_number, c.id, s.issue_date::date, s.due_date::date, s.subtotal, s.tax, s.total, s.status, s.notes,
       s.issue_date::timestamp
FROM (VALUES
    ('FAC-2026-0001', 'ana.perez@ejemplo.com',       '2026-10-05', '2026-11-04', 150000.00, 28500.00, 178500.00, 'PAID',      'Desarrollo de API REST'),
    ('FAC-2026-0002', 'ana.perez@ejemplo.com',       '2026-11-10', '2026-12-10',  85000.00, 16150.00, 101150.00, 'PENDING',   'Soporte mensual noviembre'),
    ('FAC-2026-0003', 'bruno.gonzalez@ejemplo.com',  '2026-10-20', '2026-11-19', 240000.00, 45600.00, 285600.00, 'PAID',      'Integración con Supabase'),
    ('FAC-2026-0004', 'diego.munoz@ejemplo.com',     '2026-11-15', '2026-12-15',  60000.00, 11400.00,  71400.00, 'PENDING',   'Consultoría Spring Boot'),
    ('FAC-2026-0005', 'isabel.martinez@ejemplo.com', '2026-11-01', '2026-12-01', 120000.00, 22800.00, 142800.00, 'CANCELLED', 'Licencia anual (anulada)'),
    ('FAC-2026-0006', 'camila.rojas@ejemplo.com',    '2026-11-20', NULL,          45000.00,  8550.00,  53550.00, 'PENDING',   'Capacitación en React')
) AS s (invoice_number, email, issue_date, due_date, subtotal, tax, total, status, notes)
JOIN customers c ON c.email = s.email
WHERE NOT EXISTS (SELECT 1 FROM invoices);

-- Líneas de esas facturas (la factura se busca por número). line_total no se inserta: es una
-- columna generada por PostgreSQL (round(quantity * unit_price, 2)).
INSERT INTO invoice_items (invoice_id, line_number, description, quantity, unit_price, created_at)
SELECT i.id, s.line_number, s.description, s.quantity, s.unit_price, i.created_at
FROM (VALUES
    ('FAC-2026-0001', 1, 'Diseño de endpoints REST',                      1.00,  50000.00),
    ('FAC-2026-0001', 2, 'Implementación de endpoints (horas)',          20.00,   4000.00),
    ('FAC-2026-0001', 3, 'Documentación OpenAPI',                         1.00,  20000.00),
    ('FAC-2026-0002', 1, 'Soporte técnico mensual',                       1.00,  60000.00),
    ('FAC-2026-0002', 2, 'Horas adicionales de soporte',                  5.00,   5000.00),
    ('FAC-2026-0003', 1, 'Configuración de proyecto Supabase',            1.00,  40000.00),
    ('FAC-2026-0003', 2, 'Migración de base de datos',                    1.00, 120000.00),
    ('FAC-2026-0003', 3, 'Integración Spring Boot con Supabase (horas)', 16.00,   5000.00),
    ('FAC-2026-0004', 1, 'Consultoría Spring Boot (horas)',              12.00,   5000.00),
    ('FAC-2026-0005', 1, 'Licencia anual de software',                    1.00, 100000.00),
    ('FAC-2026-0005', 2, 'Configuración inicial',                         1.00,  20000.00),
    ('FAC-2026-0006', 1, 'Taller de React (horas)',                       1.50,  30000.00)
) AS s (invoice_number, line_number, description, quantity, unit_price)
JOIN invoices i ON i.invoice_number = s.invoice_number
WHERE NOT EXISTS (SELECT 1 FROM invoice_items);
