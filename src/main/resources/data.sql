-- Seed de 20 clientes de ejemplo (datos ficticios).
-- Se ejecuta en cada arranque después de que Hibernate crea las tablas
-- (spring.jpa.defer-datasource-initialization=true). ON CONFLICT (email) lo hace
-- idempotente: si el cliente ya existe, no se vuelve a insertar.
-- created_at se asigna aquí porque @PrePersist solo corre al guardar desde JPA.

INSERT INTO customers (name, last_name, email, phone, address, city, birth_date, active, created_at)
VALUES
    ('Ana',       'Pérez',     'ana.perez@ejemplo.com',        '+56 9 1000 0001', 'Av. Siempre Viva 101',   'Santiago',     '1990-05-20', true,  now()),
    ('Bruno',     'González',  'bruno.gonzalez@ejemplo.com',   '+56 9 1000 0002', 'Calle Los Olmos 202',    'Valparaíso',   '1985-11-03', true,  now()),
    ('Camila',    'Rojas',     'camila.rojas@ejemplo.com',     '+56 9 1000 0003', 'Pasaje Las Rosas 303',   'Concepción',   '1993-02-14', true,  now()),
    ('Diego',     'Muñoz',     'diego.munoz@ejemplo.com',      '+56 9 1000 0004', 'Av. Central 404',        'La Serena',    '1988-07-09', true,  now()),
    ('Elena',     'Díaz',      'elena.diaz@ejemplo.com',       '+56 9 1000 0005', 'Calle del Sol 505',      'Antofagasta',  '1979-12-25', true,  now()),
    ('Felipe',    'Soto',      'felipe.soto@ejemplo.com',      '+56 9 1000 0006', 'Av. Libertad 606',       'Temuco',       '1995-04-30', true,  now()),
    ('Gabriela',  'Contreras', 'gabriela.contreras@ejemplo.com','+56 9 1000 0007', 'Calle Norte 707',       'Rancagua',     '1991-09-17', true,  now()),
    ('Héctor',    'Silva',     'hector.silva@ejemplo.com',     '+56 9 1000 0008', 'Pasaje Sur 808',         'Talca',        '1982-03-08', false, now()),
    ('Isabel',    'Martínez',  'isabel.martinez@ejemplo.com',  '+56 9 1000 0009', 'Av. Los Pinos 909',      'Arica',        '1997-06-21', true,  now()),
    ('Javier',    'Sepúlveda', 'javier.sepulveda@ejemplo.com', '+56 9 1000 0010', 'Calle Oriente 110',      'Iquique',      '1987-10-12', true,  now()),
    ('Karina',    'Morales',   'karina.morales@ejemplo.com',   '+56 9 1000 0011', 'Av. Poniente 211',       'Puerto Montt', '1994-01-05', true,  now()),
    ('Luis',      'Rodríguez', 'luis.rodriguez@ejemplo.com',   '+56 9 1000 0012', 'Calle Las Acacias 312',  'Chillán',      '1980-08-28', true,  now()),
    ('María',     'López',     'maria.lopez@ejemplo.com',      '+56 9 1000 0013', 'Pasaje El Bosque 413',   'Valdivia',     '1999-11-11', true,  now()),
    ('Nicolás',   'Fuentes',   'nicolas.fuentes@ejemplo.com',  '+56 9 1000 0014', 'Av. Del Mar 514',        'Viña del Mar', '1986-05-02', false, now()),
    ('Olivia',    'Hernández', 'olivia.hernandez@ejemplo.com', '+56 9 1000 0015', 'Calle Mirador 615',      'Osorno',       '1992-07-19', true,  now()),
    ('Pablo',     'Torres',    'pablo.torres@ejemplo.com',     '+56 9 1000 0016', 'Av. Cordillera 716',     'Calama',       '1983-02-27', true,  now()),
    ('Valentina', 'Araya',     'valentina.araya@ejemplo.com',  '+56 9 1000 0017', 'Calle Los Aromos 817',   'Copiapó',      '1996-12-01', true,  now()),
    ('Ricardo',   'Flores',    'ricardo.flores@ejemplo.com',   '+56 9 1000 0018', 'Pasaje Las Lilas 918',   'Punta Arenas', '1978-04-16', true,  now()),
    ('Sofía',     'Espinoza',  'sofia.espinoza@ejemplo.com',   '+56 9 1000 0019', 'Av. Las Torres 119',     'Los Ángeles',  '1998-09-23', true,  now()),
    ('Tomás',     'Valenzuela','tomas.valenzuela@ejemplo.com', '+56 9 1000 0020', 'Calle Esperanza 220',    'Quilpué',      '1989-06-06', true,  now())
ON CONFLICT (email) DO NOTHING;
