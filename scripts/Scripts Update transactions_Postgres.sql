
UPDATE cs_transaction_erp SET "authorization" = '401587' WHERE nsu='377847297';
UPDATE cs_transaction_erp SET nsu = '876033679', "authorization" = '009344' WHERE nsu='9344' and "authorization" = '8760033679';
UPDATE cs_transaction_erp SET nsu = '125960793', "authorization" = 'A2CWBH' WHERE nsu='111' and "authorization" = '125960793';
UPDATE cs_transaction_erp SET nsu = '501505361', "authorization" = '634712' WHERE nsu='634712' and "authorization" = '501505361';
UPDATE cs_transaction_erp SET nsu = '15505990', "authorization" = '570656' WHERE nsu='798921' and "authorization" = '570656';
UPDATE cs_transaction_erp SET nsu = '16840242', "authorization" = '415769' WHERE nsu='415769' and "authorization" = '798922';
UPDATE cs_transaction_erp SET nsu = '171870554', "authorization" = '620781' WHERE nsu='620781' and "authorization" = '798923';
UPDATE cs_transaction_erp SET nsu = '27529968', "authorization" = '274069' WHERE nsu='274069' and "authorization" = '449390';
UPDATE cs_transaction_erp SET nsu = '16561360', "authorization" = '548596' WHERE nsu='548596' and "authorization" = '798924';
UPDATE cs_transaction_erp SET nsu = '181998016', "authorization" = '107366' WHERE nsu='107366' and "authorization" = '4493389';
UPDATE cs_transaction_erp SET nsu = '173299620', "authorization" = 'PFQ9O2' WHERE nsu='21042026' and "authorization" = '798925';
UPDATE cs_transaction_erp SET nsu = '335755866', "authorization" = '000000' WHERE nsu='335755866' and "authorization" = '05';

UPDATE cs_transaction_erp SET acquirer_id = (SELECT id FROM cs_acquirer where fantasy_name = 'Sicredi') where nsu = '64' and "authorization"='136495';
UPDATE cs_transaction_erp SET acquirer_id = (SELECT id FROM cs_acquirer where fantasy_name = 'Sicredi') where nsu = '62' and "authorization"='143062';
UPDATE cs_transaction_erp SET acquirer_id = (SELECT id FROM cs_acquirer where fantasy_name = 'Sicredi') where nsu = '61' and "authorization"='059977';
UPDATE cs_transaction_erp SET acquirer_id = (SELECT id FROM cs_acquirer where fantasy_name = 'Sicredi') where nsu = '60' and "authorization"='561619';
UPDATE cs_transaction_erp SET acquirer_id = (SELECT id FROM cs_acquirer where fantasy_name = 'Sicredi') where nsu = '59' and "authorization"='965993';
UPDATE cs_transaction_erp SET acquirer_id = (SELECT id FROM cs_acquirer where fantasy_name = 'Sicredi') where nsu = '58' and "authorization"='514865';
UPDATE cs_transaction_erp SET acquirer_id = (SELECT id FROM cs_acquirer where fantasy_name = 'Sicredi') where nsu = '57' and "authorization"='811971';
UPDATE cs_transaction_erp SET acquirer_id = (SELECT id FROM cs_acquirer where fantasy_name = 'Sicredi') where nsu = '55' and "authorization"='D9D2DZ';
UPDATE cs_transaction_erp SET acquirer_id = (SELECT id FROM cs_acquirer where fantasy_name = 'Sicredi') where nsu = '50' and "authorization"='H9396V';
UPDATE cs_transaction_erp SET acquirer_id = (SELECT id FROM cs_acquirer where fantasy_name = 'Sicredi') where nsu = '49' and "authorization"='00KMB3';
UPDATE cs_transaction_erp SET acquirer_id = (SELECT id FROM cs_acquirer where fantasy_name = 'Sicredi') where nsu = '48' and "authorization"='CLWL1E';
UPDATE cs_transaction_erp SET acquirer_id = (SELECT id FROM cs_acquirer where fantasy_name = 'Sicredi') where nsu = '42' and "authorization"='372898';
UPDATE cs_transaction_erp SET acquirer_id = (SELECT id FROM cs_acquirer where fantasy_name = 'Sicredi') where nsu = '35' and "authorization"='V6MTDW';
UPDATE cs_transaction_erp SET acquirer_id = (SELECT id FROM cs_acquirer where fantasy_name = 'Sicredi') where nsu = '31' and "authorization"='442094';
UPDATE cs_transaction_erp SET acquirer_id = (SELECT id FROM cs_acquirer where fantasy_name = 'Sicredi') where nsu = '32' and "authorization"='d12674';
UPDATE cs_transaction_erp SET acquirer_id = (SELECT id FROM cs_acquirer where fantasy_name = 'Sicredi') where nsu = '29' and "authorization"='dvygr';
UPDATE cs_transaction_erp SET acquirer_id = (SELECT id FROM cs_acquirer where fantasy_name = 'Sicredi') where nsu = '30' and "authorization"='286400';
UPDATE cs_transaction_erp SET acquirer_id = (SELECT id FROM cs_acquirer where fantasy_name = 'Sicredi') where nsu = '20' and "authorization"='812700';
UPDATE cs_transaction_erp SET acquirer_id = (SELECT id FROM cs_acquirer where fantasy_name = 'Sicredi') where nsu = '18' and "authorization"='808794';
UPDATE cs_transaction_erp SET acquirer_id = (SELECT id FROM cs_acquirer where fantasy_name = 'Sicredi') where nsu = '17' and "authorization"='5512D5';
UPDATE cs_transaction_erp SET acquirer_id = (SELECT id FROM cs_acquirer where fantasy_name = 'Sicredi') where nsu = '15' and "authorization"='643694';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', deleted_date = '2024-07-25',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE. Venda desfeita na operadora, não conseguir identificar o motivo'
    WHERE nsu = '358459872' and "authorization"= '063004';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '2', deleted_date = '2026-01-19',
	observations = 'Venda marcada como excluída porque não foi localizada na adquirente. Motivo: UNDONE. Venda desfeita na operadora, não conseguir identificar o motivo'
    WHERE nsu = '344145992' and "authorization"= '092938';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2026-07-01',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '5028975773' and "authorization"= '004298';
	
UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2026-01-19',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '338594418' and "authorization"= '276946';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2026-01-07',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '503327746' and "authorization"= '061529';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-11-22',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '344436280' and "authorization"= 'WCNCDW';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-11-22',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '344388354' and "authorization"= '232697';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-08-16',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '503223904' and "authorization"= '147219';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-08-16',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '502409722' and "authorization"= '661586';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-05-02',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '504957078' and "authorization"= 'Eq7a2C';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-04-21',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '334819362' and "authorization"= '024209';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-04-21',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '502944938' and "authorization"= '703421';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-03-08',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '341478890' and "authorization"= '095244';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-03-04',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '502279918' and "authorization"= '181973';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-02-27',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '338836448' and "authorization"= '097099';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-02-27',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '338780594' and "authorization"= '604108';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-02-15',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '504000470' and "authorization"= '451163';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-01-27',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '503845408' and "authorization"= '030750';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-01-26',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '504244222' and "authorization"= '804773';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-01-24',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '503712666' and "authorization"= '217021';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-01-19',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '504275982' and "authorization"= '052523';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-01-18',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '341063430' and "authorization"= 'R04434';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-01-07',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '340698582' and "authorization"= '913581';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2024-12-28',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '127220233' and "authorization"= '382972';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2024-12-27',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '375960183' and "authorization"= '493772';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2024-12-15',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '503490966' and "authorization"= 'LMg6k1';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2024-11-02',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '11753659' and "authorization"= '211062';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2024-09-15',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '341216262' and "authorization"= '001674';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2024-09-15',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '340270850' and "authorization"= '002097';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2024-08-18',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '346481836' and "authorization"= '474476';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2024-08-18',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '346804934' and "authorization"= '935947';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2024-07-27',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '344510522' and "authorization"= 'j5bnYz';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-07-11',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '509530300' and "authorization"= '967310';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-07-11',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '340779914' and "authorization"= 'rXyeJW';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-07-11',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '509199620' and "authorization"= '052962';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-07-11',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '509055758' and "authorization"= '040856';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-07-11',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '340885352' and "authorization"= '786325';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-07-11',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '509280300' and "authorization"= 'MTCYUR';

UPDATE cs_transaction_erp SET status_transaction = '4', reason_exclusion_status = '6', status_transaction_reason = '6', canceled_date = '2025-07-11',
	observations = 'Venda Cancelada no ERP, no mesmo dia: CANCELED.'
    WHERE nsu = '340933162' and "authorization"= '087361';


	
