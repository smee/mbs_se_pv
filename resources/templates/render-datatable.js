(function(baseUrl){
	$(document).ready(function() {
		$('#names').dataTable( {
	    'sDom': "<'row'<'span5 doNotFloat'l><'span5 doNotFloat'f>r>t<'row'<'span3'i><'span7'p>>",
			'bProcessing': true,
			'bServerSide': true,
			'sAjaxSource': baseUrl+'/data/metadata.json', 
	    'bPaginate': true,
	    'sPaginationType': 'bootstrap',
	    'bStateSave': true,
	     'oLanguage': {
				'sLengthMenu': 'Zeige _MENU_ Einträge pro Seite',
				'sZeroRecords': 'Kein Eintrag gefunden!',
				'sInfo': 'Zeige Einträge _START_ bis _END_ von _TOTAL_ vorhandenen',
				'sInfoEmpty': 'Zeige Einträge 0 bis 0 von 0 vorhandenen',
				'sInfoFiltered': '(von insgesamt _MAX_ Einträgen)',
	      'sProcessing': 'Suche nach passenden Einträgen',
	      'sSearch': 'Suche:'
			},
	    'aoColumns': [
	            {}, {sClass: 'alignRight'}, {sClass: 'alignRight'}, {}
	            ]
		});
	});
})("%s")