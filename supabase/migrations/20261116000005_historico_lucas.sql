-- Luluco Reef v0.3: leituras de baixa confianca, dados atuais do aquario (125 L, Blue Treasure),
-- inventario real (substitui o seed) e historico antigo de testes, TPAs e eventos.
-- Idempotente: ids deterministicos (md5) + "on conflict do nothing"; o inventario so e trocado uma vez.
-- Horarios em America/Sao_Paulo.

alter table public.water_tests add column if not exists baixa_confianca boolean not null default false;

do $$
declare
  uid uuid;
  tz constant text := 'America/Sao_Paulo';
  marker uuid := md5('luluco-v03|inventario')::uuid;
begin
  select id into uid from auth.users order by created_at limit 1;
  if uid is null then
    raise exception 'Nenhum usuario em auth.users.';
  end if;

  -- 5. Dados do aquario (so na primeira vez, para nao desfazer edicoes posteriores)
  if not exists (select 1 from public.events where id = marker) then
    update public.tank_config
       set volume_dosagem_l = 125,
           sal_atual = 'Blue Treasure Reef Salt',
           observacoes = 'Marinho LPS dominante, cubo 50x50x50 com sump traseiro. Testes: kits Aquaforest de KH, Ca e Mg; salinidade com refratômetro ATC (zero calibrado com água de osmose).',
           updated_at = now()
     where user_id = uid and deleted_at is null;

    -- 6. Inventario: apaga (exclusao logica) tudo que havia e insere a lista real
    update public.livestock set deleted_at = now(), updated_at = now() where user_id = uid and deleted_at is null;

    insert into public.livestock (id, user_id, tipo, nome_popular, nome_cientifico, categoria, quantidade, local_no_aquario, revisar, observacao)
    select md5('luluco-v03|bicho|' || v.nome)::uuid, uid, v.tipo, v.nome, v.cientifico, v.categoria, v.qtd, v.local_aq, false, v.obs
    from (values
      ('peixe', 'Ocellaris Chocolate',        'Amphiprion ocellaris',   null,  1, null, null),
      ('peixe', 'Ocellaris Storm Moca',       'Amphiprion ocellaris',   null,  1, null, null),
      ('peixe', 'Donzela Springeri',          'Chrysiptera springeri',  null,  1, null, null),
      ('peixe', 'Yellow Coral Goby',          'Gobiodon okinawae',      null,  1, null, null),
      ('peixe', 'Goby cingido',               'Priolepis cincta',       null,  1, null, 'Girdled goby.'),
      ('coral', 'Montipora setosa',           'Montipora setosa',       'SPS', 1, null, 'Conhecida como "Cetosa".'),
      ('coral', 'Hydnophora',                 'Hydnophora sp.',         'SPS', 1, null, null),
      ('coral', 'Torch Black Rasta (1 boca)', 'Euphyllia glabrescens',  'LPS', 1, null, '1 boca.'),
      ('coral', 'Torch Black Rasta (2 bocas)','Euphyllia glabrescens',  'LPS', 1, null, '2 bocas.'),
      ('coral', 'Torch Hulk',                 'Euphyllia glabrescens',  'LPS', 1, null, '2 bocas.'),
      ('coral', 'Torch Dragon Soul',          'Euphyllia glabrescens',  'LPS', 1, null, '1 boca.'),
      ('coral', 'Torch Holy Grail',           'Euphyllia glabrescens',  'LPS', 1, null, '2 bocas.'),
      ('coral', 'Duncan',                     'Duncanopsammia axifuga', 'LPS', 1, null, '4 bocas.'),
      ('coral', 'Blastomussa verde com roxo', 'Blastomussa sp.',        'LPS', 1, null, null),
      ('coral', 'Blastomussa wellsi',         'Blastomussa wellsi',     'LPS', 1, null, null),
      ('coral', 'Chalice Avatar',             null,                     'LPS', 1, null, null),
      ('coral', 'Chalice Raja Rampage',       null,                     'LPS', 1, null, null),
      ('coral', 'Chalice Rainbow',            null,                     'LPS', 1, null, null),
      ('coral', 'Acanthastrea bicolor',       'Acanthastrea sp.',       'LPS', 1, null, null),
      ('coral', 'Acanthastrea Cotton Candy',  'Acanthastrea sp.',       'LPS', 1, null, null),
      ('coral', 'Lobophyllia bicolor',        'Lobophyllia sp.',        'LPS', 1, null, null),
      ('coral', 'Goniopora Ultra Red',        'Goniopora sp.',          'LPS', 1, null, null),
      ('coral', 'Goniopora Green',            'Goniopora sp.',          'LPS', 1, null, null),
      ('coral', 'Ricordea yuma orange',       'Ricordea yuma',          'mole',1, null, null),
      ('coral', 'Ricordea yuma rainbow',      'Ricordea yuma',          'mole',1, null, null),
      ('coral', 'Plate Dragon Wheel',         null,                     'LPS', 1, null, null),
      ('coral', 'Hammer Wall',                null,                     'LPS', 1, null, null),
      ('coral', 'Hammer Hologram',            null,                     'LPS', 1, null, null),
      ('coral', 'Hammer Gold',                null,                     'LPS', 4, null, null),
      ('coral', 'Hammer Solomon',             null,                     'LPS', 1, null, null),
      ('coral', 'Hammer Jelly Bean',          null,                     'LPS', 1, null, null),
      ('coral', 'Hammer Fireball',            null,                     'LPS', 1, null, null),
      ('coral', 'Hammer Orange',              null,                     'LPS', 1, null, null),
      ('coral', 'Frammer Gold',               null,                     'LPS', 1, null, null),
      ('coral', 'Frogspawn verde com roxo',   null,                     'LPS', 1, null, null),
      ('coral', 'Bubble branco',              null,                     'LPS', 1, null, null),
      ('coral', 'Zoanthus Stargazer',         'Zoanthus sp.',           'zoa', 1, 'rocha do jardim de zoas', null),
      ('coral', 'Zoanthus Alpha e Ômega',     'Zoanthus sp.',           'zoa', 1, 'rocha do jardim de zoas', null),
      ('coral', 'Zoanthus Rasta',             'Zoanthus sp.',           'zoa', 1, 'rocha do jardim de zoas', null),
      ('coral', 'Zoanthus GMK',               'Zoanthus sp.',           'zoa', 1, 'rocha do jardim de zoas', null),
      ('coral', 'Zoanthus Tree of Horror',    'Zoanthus sp.',           'zoa', 1, 'rocha do jardim de zoas', null),
      ('coral', 'Zoanthus King Midas',        'Zoanthus sp.',           'zoa', 1, 'rocha do jardim de zoas', null),
      ('coral', 'Zoanthus Yoda',              'Zoanthus sp.',           'zoa', 1, 'rocha do jardim de zoas', null),
      ('coral', 'Palythoa Stratosphere',      'Palythoa sp.',           'zoa', 1, 'rocha do jardim de zoas', null),
      ('coral', 'Palythoa Magician',          'Palythoa sp.',           'zoa', 1, 'rocha do jardim de zoas', null),
      ('coral', 'Palythoa People Eater',      'Palythoa sp.',           'zoa', 1, 'rocha do jardim de zoas', null)
    ) as v(tipo, nome, cientifico, categoria, qtd, local_aq, obs)
    on conflict (id) do nothing;

    insert into public.events (id, user_id, ocorrido_em, tipo, texto)
    values (marker, uid, now(), 'outro', 'Atualização v0.3: volume de dosagem 125 L, sal Blue Treasure e inventário substituído pela lista real.');
  end if;

  -- 7. Historico de testes
  insert into public.water_tests (id, user_id, medido_em, parametro, valor, unidade, kit, baixa_confianca, observacao)
  select md5('luluco-v03|teste|' || v.quando || '|' || v.parametro)::uuid, uid, (v.quando::timestamp at time zone tz), v.parametro, v.valor,
         case v.parametro when 'kh' then 'dKH' when 'salinidade' then 'SG' else 'ppm' end,
         case v.parametro when 'salinidade' then 'Refratômetro ATC' else 'Aquaforest' end,
         v.baixa, v.obs
  from (values
    ('2026-10-26 12:00', 'kh',         7.8,    false, 'Horário não registrado.'),
    ('2026-10-26 12:00', 'ca',         500,    true,  'Acima de 500, fora da escala do kit.'),
    ('2026-10-26 12:00', 'mg',         1140,   false, 'Horário não registrado.'),
    ('2026-10-30 02:04', 'kh',         7.9,    false, null),
    ('2026-10-30 02:04', 'ca',         550,    false, 'Amostra diluída 1:1, resultado x2.'),
    ('2026-10-30 02:04', 'mg',         990,    true,  'Reteste às 02:16.'),
    ('2026-10-30 02:16', 'mg',         1030,   true,  'Reteste, entre 1020 e 1080.'),
    ('2026-10-30 02:40', 'salinidade', 1.0225, false, '1,022 a 1,023; refratômetro, zero calibrado com osmose. Leituras antigas ~1,025 eram erro de método.'),
    ('2026-10-30 15:00', 'salinidade', 1.0238, false, '1,0235 a 1,024.'),
    ('2026-10-31 19:26', 'salinidade', 1.025,  false, 'Leituras 1,026 e 1,024.'),
    ('2026-11-04 15:15', 'kh',         8.4,    true,  'Lido na 2ª virada, superestima ~0,5 a 0,7.'),
    ('2026-11-04 15:15', 'ca',         500,    false, null),
    ('2026-11-04 15:15', 'mg',         1200,   false, 'Parando na 1ª virada.'),
    ('2026-11-04 15:40', 'salinidade', 1.026,  false, null),
    ('2026-11-05 16:05', 'kh',         8.0,    true,  '2ª virada.'),
    ('2026-11-06 22:37', 'kh',         8.2,    true,  'Entre 8,0 e 8,4; 2ª virada.'),
    ('2026-11-07 11:53', 'kh',         7.7,    false, '1ª virada (2ª: 8,4; 3ª: 8,7).'),
    ('2026-11-09 15:10', 'kh',         7.7,    false, '1ª virada (2ª: 7,9).'),
    ('2026-11-10 15:50', 'kh',         7.6,    false, '1ª virada (2ª: 7,8).'),
    ('2026-11-11 22:34', 'kh',         7.85,   false, '7,8 a 7,9, 1ª virada.')
  ) as v(quando, parametro, valor, baixa, obs)
  on conflict (id) do nothing;

  -- TPAs
  insert into public.water_changes (id, user_id, feita_em, litros, sal, salinidade_nova_agua, observacao)
  select md5('luluco-v03|tpa|' || v.quando)::uuid, uid, (v.quando::timestamp at time zone tz), v.litros, v.sal, v.salin, v.obs
  from (values
    ('2026-10-28 23:50', 20,   'Aquaforest Reef Salt',    null::numeric, null),
    ('2026-11-03 22:30', 14.5, 'Blue Treasure Reef Salt', 1.026,         '14 a 15 L.')
  ) as v(quando, litros, sal, salin, obs)
  on conflict (id) do nothing;

  -- Eventos
  insert into public.events (id, user_id, ocorrido_em, tipo, texto)
  select md5('luluco-v03|evento|' || v.quando)::uuid, uid, (v.quando::timestamp at time zone tz), v.tipo, v.texto
  from (values
    ('2026-10-30 13:00', 'observacao', 'Ajuste de salinidade com salmoura: 4 L de água do aquário a 1,048.'),
    ('2026-10-31 13:00', 'observacao', 'Ajuste de salinidade com salmoura: 4 L a 1,050.'),
    ('2026-11-03 23:00', 'observacao', 'Teste da água nova Blue Treasure a 1,025, sem entrar no aquário: KH 7,7, Ca 400, Mg 1200 (referência do sal, não é medição do aquário).'),
    ('2026-11-06 22:00', 'observacao', 'Entraram 6 Euphyllia novos, incluindo um Hammer Wall grande.'),
    ('2026-11-07 11:00', 'observacao', 'Método do KH mudou: leitura na 1ª virada para rosa a partir de hoje.'),
    ('2026-11-11 23:00', 'observacao', 'Consumo de KH praticamente zero entre 07/11 e 11/11; All-For-Reef não iniciado; TPAs com Blue Treasure repondo o necessário.')
  ) as v(quando, tipo, texto)
  on conflict (id) do nothing;
end $$;
