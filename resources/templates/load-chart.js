$('%s').click(
		function(event) {
			(function(baseUrl, id) {
				event.preventDefault();
				// create selected date interval: yyyyMMdd-yyyyMMdd
				var start = $('#start-date').DatePickerGetDate(false);
				var end = $('#end-date').DatePickerGetDate(false);
				var m1 = start.getMonth() + 1;
				var m2 = end.getMonth() + 1;
				var d1 = start.getDate();
				var d2 = end.getDate();
				var startDate = '' + start.getFullYear() + (m1 < 10 ? '0' + m1 : m1) + (d1 < 10 ? '0' + d1 : d1);
				var endDate = '' + end.getFullYear() + (m2 < 10 ? '0' + m2 : m2) + (d2 < 10 ? '0' + d2 : d2);
				var interval = startDate + '-' + endDate;
				// create list of selected time series
				var selectedSeries = $.map($('#series-tree').dynatree('getSelectedNodes'), function(node) {
					return node.data.series;
				});
				// do not fetch a chart without any selected series
				if (selectedSeries.length < 1)
					return false;
				// find selected chart type
				var visType = $("#chart-type").val();
				// remove old contents
				var chartDiv = $('#current-chart');
				var width = $('#chart-width').val();
				var height = $('#chart-height').val();
				chartDiv.empty();

				$('#current-chart').showLoading();

				if (visType == 'interactive-map') {
					ensure({
						js : "/js/chart/leaflet.js",
						css : "/css/chart/leaflet.css"
					}, function() {
						chartDiv.append($("<div id='interactive-map'/>"));
						var map = L.map('interactive-map').setView([ 51.505, -0.09 ], 2);
						L.tileLayer(baseUrl + '/tiles/' + id + '/' + selectedSeries.join('-') + '/' + interval + '/{x}/{y}/{z}', {
							attribution : 'done by me :)',
							noWrap : true,
							maxZoom : 10
						}).addTo(map);
						$('#current-chart').hideLoading();
					});

				} else if (visType == 'interactive-client') {
					ensure({
						js : [ baseUrl+"/js/chart/d3.v2.min.js", baseUrl+"/js/chart/d3.layout.min.js", baseUrl+"/js/chart/rickshaw.min.js" ],
						css : baseUrl+"/css/chart/rickshaw.min.css"
					}, function() {
						chartDiv.append($("<div id='chart_container'><div id='y_axis'/><div id='chart'/></div><div id=\'legend\'/>"));
						// create map of iec61850 names to human readable labels
						var seriesLabelsMap = $('#series-tree').dynatree('getSelectedNodes').reduce(function(m, sel) {
							var title = sel.data.title;
							var id = sel.data.series;
							if (id) {
								var prefix = id.substring(0, id.indexOf('/'));
								m[id] = prefix + "|" + sel.data.title;
							}
							;
							return m;
						}, new Object());

						var palette = new Rickshaw.Color.Palette();

						var series = [];
						for (idx in selectedSeries)
							series.push({
								key : selectedSeries[idx],
								name : seriesLabelsMap[selectedSeries[idx]],
								color : palette.color()
							});

						ajaxGraph = new Rickshaw.Graph.Ajax({
							element : document.getElementById("chart"),
							width : width,
							height : height,
							renderer : 'line',
							min : 'auto',
							dataURL : baseUrl + '/series-of/' + id + '/' + selectedSeries.join('-') + '/' + interval + '/data.json?width=' + width,
							onData : function(d) {
								Rickshaw.Series.zeroFill(d);
								return d
							},
							series : series,
							onComplete : function(ajaxGraph) {
								$('#current-chart').hideLoading();
								var graph = ajaxGraph.graph;
								var axes = new Rickshaw.Graph.Axis.Time({
									graph : graph
								});
								var y_axis = new Rickshaw.Graph.Axis.Y({
									graph : graph,
									orientation : 'left',
									tickFormat : Rickshaw.Fixtures.Number.formatKMBT,
									element : document.getElementById('y_axis')
								});
								var legend = new Rickshaw.Graph.Legend({
									element : document.querySelector('#legend'),
									graph : graph
								});
								var hover = new Rickshaw.Graph.HoverDetail({
									graph : ajaxGraph.graph
								});
								var shelving = new Rickshaw.Graph.Behavior.Series.Toggle({
									graph : graph,
									legend : legend
								});
								var highlighter = new Rickshaw.Graph.Behavior.Series.Highlight({
									graph : graph,
									legend : legend
								});

								graph.render();
							}
						});
					});

				}else if (visType == 'dygraph'){
					ensure({
						js : [ baseUrl+"/js/chart/dygraph-combined.js"],
						css : []
					}, function() {
						chartDiv.append($("<div id='dygraph-chart'/>"));
						g = new Dygraph(
							    // containing div
								$('#dygraph-chart')[0],
								baseUrl + '/series-of/' + id + '/' + selectedSeries.join('-') + '/' + interval + '/data-dyson.csv?width=' + width,
							    { width: width,
								  height: height,
								  customBars: true,
								  showRoller: true,
								  labelsKMB :true,
								  labelsSeparateLines: true,
							        highlightSeriesOpts: {
							            strokeWidth: 3,
							            strokeBorderWidth: 1,
							            highlightCircleSize: 5,
							          },
								  drawCallback: function(graph,isInitial){ if(isInitial) $('#current-chart').hideLoading();}
								});
					});
				} else { //static image
					chartDiv.append($("<img id='chart-image' src=''/>"));
					// create link
					var link = baseUrl + '/series-of/' + id + '/' + selectedSeries.join('-') + '/' + interval + '/' + visType + '.png?width='
							+ width + '&height=' + height;
					if(visType == 'changepoints'){
						link += '&rank='+$('input#rank').is(':checked')
						       +'&zero='+$('input#zero').is(':checked')
						       +'&negative='+$('input#negative').is(':checked')
						       +'&confidence='+$('input#confidence').val()
						       +'&max-level='+$('input#max-level').val()
						       +'&maintainance='+$('input#maintainance').is(':checked');
					}
					// show chart
					$('#chart-image').attr('src', link).load(function() {
						$('#current-chart').hideLoading();
					});
				}
				return false;
			})("%s", "%s");

		});
