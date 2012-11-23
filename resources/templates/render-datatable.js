(function(baseUrl) {
	$(document).ready(function() {
		ensure({ js: baseUrl+"/js/jquery.dataTables.min.js"}, function() { //TODO why doesn't ensure evaluate the javascript files in declaration order?
			ensure({js: baseUrl+"/js/dataTables.paging.bootstrap.js"}, function(){
				// compatibility between datatables and bootstrap
				// see http://www.datatables.net/blog/Twitter_Bootstrap
				$.extend($.fn.dataTableExt.oStdClasses, {
					'sWrapper' : 'dataTables_wrapper form-inline'
				});
				$('#names').dataTable({
					'sDom' : "<'row'<'span6'l><'span6'f>r>t<'row'<'span3'i><'span9'p>>",
					'bProcessing' : true,
					'bServerSide' : true,
					'sAjaxSource' : baseUrl + '/data/metadata.json',
					'bPaginate' : true,
					'sPaginationType' : 'bootstrap',
					'bStateSave' : true,
					'oLanguage' : {
						'sLengthMenu' : 'Zeige _MENU_ Einträge pro Seite',
						'sZeroRecords' : 'Kein Eintrag gefunden!',
						'sInfo' : 'Zeige Einträge _START_ bis _END_ von _TOTAL_ vorhandenen',
						'sInfoEmpty' : 'Zeige Einträge 0 bis 0 von 0 vorhandenen',
						'sInfoFiltered' : '(von insgesamt _MAX_ Einträgen)',
						'sProcessing' : 'Suche nach passenden Einträgen',
						'sSearch' : 'Suche:',
						'oPaginate' : {
							'sPrevious' : 'Vorherige',
							'sNext' : 'Nächste'
						}
					},
					'aoColumns' : [ {}, {sClass : 'alignRight'}, {sClass : 'alignRight'}, {} ]
				});
			});			
		});
	});
})("%s")