/*
 * URL e chave publicavel do Supabase. A chave "publishable" e feita para ficar no cliente: quem
 * protege os dados e o RLS do banco (cada tabela tem politica de dono e revoke para anonimo), nao o
 * sigilo dela. Ela ja esta no gradle.properties deste mesmo repositorio, usada pelo app Android.
 */
const CONFIG = {
  url: 'https://gzfkcryebczjmuzckxuv.supabase.co',
  key: 'sb_publishable_iIBPvVNZoc1PRNNEdbrK_w_CRTwmzYr',
};
