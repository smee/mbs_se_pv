(function(chartSelector,colorClass,basePath, dataUrl,maxValue){
	var data; // loaded asynchronously

	var path = d3.geo.path().projection(d3.geo.albers().origin([11,53]).translate([200,100]).scale(3000));

	var mainsvg = d3.select(chartSelector).append("svg");
	
	var svg = mainsvg
	.call(d3.behavior.zoom().on("zoom", redraw))
	.append("g");

	var states = svg.append("g")
	.attr("id", "plz")
	.attr("class", colorClass);

	mainsvg
	  .append("g")
	    .attr("class","legend "+colorClass)
	  .selectAll("rect")
	  .data(d3.range(0,9))
      .enter()
	    .append("rect")
	      .attr("y", function(d, i) { return i * 40; })
	      .attr("class",function(d){return "q"+d+"-9"})
	      .attr("width",15)
	      .attr("height",40);
	
	d3.json(basePath+"/data/plz-simple.json", function(json) {
		states.selectAll("path")
		.data(json.features)
		.enter()
		.append("path")
		.attr("class", data ? quantize : null)
		.attr("d", path)
		.on("click", function(d,i) { window.open(basePath+'/plz/'+d.properties.PLZ99); })
		.append("title")
		.text(function(d) { return d.properties.PLZ99 + " " + d.properties.PLZORT99; });
	});

	function redraw() {
		svg.attr("transform", "translate(" + d3.event.translate + ")scale(" + d3.event.scale + ")");
	}
	d3.json(dataUrl, function(json) {
		data = json;
		states.selectAll("path")
		.attr("class", quantize);
	});

	function quantize(d) {
		return "q" + Math.min(8, ~~(data[d.properties.PLZ99] * 8 / maxValue)) + "-9";
	}
})("#%s","%s","%s","%s",%d);