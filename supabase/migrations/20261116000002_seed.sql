-- Luluco Reef - dados iniciais (spec secoes 7, 9 e 14).
-- Roda uma vez para o unico usuario do projeto. Se ja houver tank_config para ele, nao faz nada.
do $$
declare
  uid uuid;
  p_goni uuid := gen_random_uuid();
  p_amino uuid := gen_random_uuid();
  p_multivit uuid := gen_random_uuid();
  p_dust uuid := gen_random_uuid();
  p_upmax uuid := gen_random_uuid();
  p_iodine uuid := gen_random_uuid();
  p_afr uuid := gen_random_uuid();
  p_dip uuid := gen_random_uuid();
  p_carvao uuid := gen_random_uuid();
begin
  select id into uid from auth.users order by created_at limit 1;
  if uid is null then
    raise exception 'Nenhum usuario em auth.users. Crie o usuario no painel (Authentication) antes de rodar o seed.';
  end if;
  if exists (select 1 from public.tank_config where user_id = uid) then
    raise notice 'Seed ja aplicado para %, nada a fazer.', uid;
    return;
  end if;

  insert into public.tank_config (user_id, nome, volume_dosagem_l, sal_atual, observacoes) values
    (uid, 'Luluco Reef', 100, 'Aquaforest Reef Salt',
     'Marinho LPS dominante, cubo 50x50x50 com sump traseiro. Doses calculadas sobre 100 L, sem somar o sump.');

  insert into public.param_ranges (user_id, parametro, ideal_min, ideal_max, alerta_min, alerta_max, unidade) values
    (uid, 'kh',          7.5,   9.0,   7.0,   10.0,  'dKH'),
    (uid, 'ca',          400,   450,   380,   480,   'ppm'),
    (uid, 'mg',          1250,  1400,  1200,  1450,  'ppm'),
    (uid, 'salinidade',  1.024, 1.026, 1.023, 1.027, 'SG'),
    (uid, 'temperatura', 25.0,  26.5,  24.0,  28.0,  '°C'),
    (uid, 'no3',         2,     10,    0.5,   25,    'ppm'),
    (uid, 'po4',         0.03,  0.10,  0.01,  0.20,  'ppm');

  -- Produtos: dose_quantidade vale para dose_base_litros; o app recalcula para o volume do aquario.
  insert into public.products (id, user_id, nome, marca, tipo, dose_quantidade, dose_unidade, dose_base_litros, observacao) values
    (p_goni,     uid, 'Goni Supreme',   'Shask', 'suplemento', 1,    'colher', 400,  '1 colher para 400 L. Em 100 L: 1/4 de colher.'),
    (p_amino,    uid, 'Amino Coral',    'Shask', 'suplemento', 2,    'gotas',  100,  '2 gotas para 100 L.'),
    (p_multivit, uid, 'Multivit Coral', 'Shask', 'suplemento', 1,    'g',      200,  '1 g para 200 L. Em 100 L: 0,5 g.'),
    (p_dust,     uid, 'Supreme Dust',   'Shask', 'suplemento', 2,    'colher', 100,  '2 colheres para 100 L.'),
    (p_upmax,    uid, 'Up Max Coral',   'Shask', 'suplemento', 2,    'g',      100,  '2 g para 100 L.'),
    (p_iodine,   uid, 'Iodine',         'Shask', 'suplemento', 1,    'gotas',  null, '1 gota (dose fixa).'),
    (p_afr,      uid, 'All-For-Reef',   'Tropic Marin', 'suplemento', null, 'ml', null, 'Dose a definir pelo Lucas. Rotina fica inativa até preencher.'),
    (p_dip,      uid, 'Supreme Dip',    'Shask', 'tratamento', 10,   'gotas',  1,    'Banho de entrada de coral: 10 gotas por litro de água do aquário em pote separado, 3 a 5 minutos agitando a água, depois o coral vai para o aquário. Sem rotina recorrente.'),
    (p_carvao,   uid, 'Carvão ativado', null,    'midia_filtrante', null, null, null, 'Troca a cada 4 semanas.');

  -- Rotinas de dosagem (20h). dias_semana ISO: 1=seg ... 7=dom.
  insert into public.routines (user_id, titulo, tipo, product_id, recorrencia, dias_semana, intervalo_n, horario, instrucao, ativa) values
    (uid, 'Goni Supreme',   'dosagem', p_goni,     'diaria',         null,          null, '20:00', null, true),
    (uid, 'Amino Coral',    'dosagem', p_amino,    'dias_da_semana', '{1,3,5}',     null, '20:00', null, true),
    (uid, 'Multivit Coral', 'dosagem', p_multivit, 'dias_da_semana', '{2,4}',       null, '20:00', null, true),
    (uid, 'Supreme Dust',   'dosagem', p_dust,     'dias_da_semana', '{2,4}',       null, '20:00', null, true),
    (uid, 'Up Max Coral',   'dosagem', p_upmax,    'dias_da_semana', '{2,4}',       null, '20:00', null, true),
    (uid, 'Iodine',         'dosagem', p_iodine,   'dias_da_semana', '{3}',         null, '20:00', null, true),
    (uid, 'All-For-Reef',   'dosagem', p_afr,      'diaria',         null,          null, '20:00', 'Preencha a dose no produto para ativar.', false);

  -- Rotinas de manutencao.
  insert into public.routines (user_id, titulo, tipo, product_id, recorrencia, dias_semana, intervalo_n, horario, instrucao, ativa) values
    (uid, 'Testes KH, Ca, Mg e salinidade', 'teste',      null,     'dias_da_semana',   '{3}', null, '18:00', 'Testar antes da TPA.', true),
    (uid, 'TPA',                            'tpa',        null,     'dias_da_semana',   '{3}', null, '19:00', '10 a 12 L.', true),
    (uid, 'Troca do perlon',                'manutencao', null,     'dias_da_semana',   '{3}', null, '19:00', 'Junto da TPA.', true),
    (uid, 'Limpeza do vidro',               'manutencao', null,     'dias_da_semana',   '{3}', null, '19:00', null, true),
    (uid, 'Troca do carvão ativado',        'manutencao', p_carvao, 'a_cada_n_semanas', null,  4,    '19:00', 'Informe a data da última troca para calcular a próxima.', true);

  -- Inventario (todos com revisar = true).
  insert into public.livestock (user_id, tipo, nome_popular, nome_cientifico, categoria, quantidade, local_no_aquario, revisar, observacao) values
    (uid, 'coral', 'Euphyllia martelo', null,                    'LPS',  4, null, true, 'Quantidade a confirmar: havia 4 e chegaram fragmentos de um amigo.'),
    (uid, 'coral', 'Frammer',           null,                    'LPS',  1, null, true, null),
    (uid, 'coral', 'Frogspawn',         null,                    'LPS',  1, null, true, null),
    (uid, 'coral', 'Bubble',            null,                    'LPS',  1, null, true, null),
    (uid, 'coral', 'Duncan',            'Duncanopsammia axifuga','LPS',  1, null, true, null),
    (uid, 'coral', 'Goniopora',         'Goniopora sp.',         'LPS',  1, null, true, null),
    (uid, 'coral', 'Coral placa',       null,                    'LPS',  2, null, true, null),
    (uid, 'coral', 'Lobophyllia',       'Lobophyllia sp.',       'LPS',  1, null, true, null),
    (uid, 'coral', 'Caulastrea',        'Caulastrea sp.',        'LPS',  2, null, true, null),
    (uid, 'coral', 'Acanthastrea',      'Acanthastrea sp.',      'LPS',  1, null, true, null),
    (uid, 'coral', 'Rocha de zoanthus', 'Zoanthus sp.',          'zoa',  1, null, true, null),
    (uid, 'coral', 'Tocha',             null,                    'LPS',  1, null, true, '6 cabeças.'),
    (uid, 'coral', 'Couro japonês',     null,                    'mole', 1, null, true, null),
    (uid, 'coral', 'Cogumelo',          null,                    'mole', 2, null, true, null),
    (uid, 'coral', 'Blastomussa',       'Blastomussa sp.',       'LPS',  2, null, true, null),
    (uid, 'coral', 'Slime ball',        null,                    'SPS',  1, null, true, 'Nome de loja; espécie a confirmar.'),
    (uid, 'coral', 'Cetosa',            null,                    'SPS',  1, null, true, 'Nome de loja; espécie a confirmar.'),
    (uid, 'coral', 'Montipora',         'Montipora sp.',         'SPS',  1, null, true, 'Frag.'),
    (uid, 'anemona', 'BBT Rainbow',     'Entacmaea quadricolor', null,   1, 'Breeder box no topo do aquário', true, 'BTA.'),
    (uid, 'anemona', 'Fire Red',        'Entacmaea quadricolor', null,   1, 'Breeder box no topo do aquário', true, 'BTA.'),
    (uid, 'peixe', 'Palhaço',           null,                    null,   2, null, true, null),
    (uid, 'peixe', 'Springeri (donzela)', 'Chrysiptera springeri', null, 1, null, true, null),
    (uid, 'peixe', 'Priolepis',         'Priolepis sp.',         null,   1, null, true, 'Espécie não confirmada.'),
    (uid, 'peixe', 'Halichoeres maculipinna', 'Halichoeres maculipinna', null, 1, null, true, null);

  insert into public.equipment (user_id, tipo, marca, modelo, configuracao, observacao) values
    (uid, 'skimmer',          null,               'AE-Nano 50D', null, null),
    (uid, 'bomba_circulacao', 'Jebao',            'SOW3N',       '900 a 3000 L/h', null),
    (uid, 'luminaria',        'AquaIllumination', 'Prime 16HD',  'A cerca de 25 cm da água', null),
    (uid, 'termostato',       null,               'STC-1000',    null, 'Controla a temperatura. O app só exibe.'),
    (uid, 'osmose',           null,               'Osmose reversa', null, 'Com medidor de TDS.'),
    (uid, 'bomba_retorno',    null,               null,          null, 'Modelo a definir.');
end $$;
