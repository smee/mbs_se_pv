(function (EntropyChart, $, baseUrl, dataUrl, selector, plantId, undefined){

var margin = {top: 200, right: 100, bottom: 10, left: 200},
    width = height = 400;
var //x = d3.scale.ordinal().rangeBands([0, width]),
    c = d3.scale.linear().domain([0,0.001,1,3]).range(["red","green","yellow","red"]);

var maindiv = d3.select(selector);

var svg = maindiv
            .append("svg")
              .attr("width", width + margin.left + margin.right)
              .attr("height", height + margin.top + margin.bottom);
var g = svg.append("g")
                .attr("transform", "translate(" + margin.left + "," + margin.top + ")");
var tooltip = maindiv.append("div").attr("class","input-prepend");
tooltip.append("span").attr("class","add-on").text("Zustand am");

//var slider = tooltip.append("input")
//               .attr("type", "number")
//               .attr("min",0)
//               .attr("value",0);
var slider = tooltip.append("select");
var summary = maindiv.append("div");

function populate(day){
	var matrix = [],
    	n = day.probabilities.length;
	
	for(var i=0;i<n;i++){
		var es = day.entropies[i];
        matrix[i] = d3.range(n).map(function(j) { return {x: j, y: i, z: es[j] || 0}; });
    };
    
    return {matrix: matrix,
    	    probabilities: day.probabilities,
    	    date: day.date};
}

d3.json(dataUrl, function(json) {
    var names = json.names,
        ids = json.ids,
        n = names.length,
        days = json.days;

    var data = populate(days[days.length-1]);
    
    var x=d3.scale.ordinal().domain(d3.range(n)).rangeBands([0, Math.min(width,n*40)]);
    var xlen=x.rangeBand()*n;
    svg.attr("width", xlen + margin.left + margin.right)
       .attr("height", xlen + margin.top + margin.bottom);
    
    // date slider/dropdown
    slider.on("change", redraw)
          .selectAll("option")
          .data(days.map(function(d){return d.date;}))
          .enter()
            .append("option")
            .text(function(d){return d;});
    slider.node().selectedIndex=days.length-1;
    
    g.append("rect")
        .attr("class", "background")
        .attr("width", xlen)
        .attr("height", xlen);

    // date label top left
    svg.append("text")
       .attr("x", 20)
       .attr("y", 20)
       .attr("class","datelabel")
       .text(data.date)
       .style("font-weight", "bold")
       .style("font-size", "large");

    // probabilities
    g.selectAll("text.problabel").data(data.probabilities)
       .enter()
       .append("text")
         .attr("class","problabel")
         .attr("x",xlen+x.rangeBand())
         .attr("y", function(d,i){return x(i)+x.rangeBand()/2;})
         .attr("dy", ".32em")
         .text(function(d){return (d*100).toFixed(0)+"%%";});
    
    g.append("text")
      .attr("x", 6)
      .attr("y", xlen+x.rangeBand())
      .attr("dx", ".32em")
      .attr("transform", "translate(" + x(data.probabilities.length) + ")rotate(-90)")
      .text("Gesamtwahrscheinlichkeit");
    
    // rows of cells
    var rows = g.selectAll(".row")
        .data(data.matrix);
        
    var row = rows.enter().append("g")
          .attr("class", "row")
          .attr("transform", function(d, i) { return "translate(0," + x(i) + ")"; })
          .each(drawrow);
    
    row.append("line")
        .attr("x2", xlen);

    row.append("text")
        .attr("x", -6)
        .attr("y", x.rangeBand() / 2)
        .attr("dy", ".32em")
        .attr("text-anchor", "end")
        .attr("class","matrixlabel")
        .text(function(d, i) { return names[i]; })
        .on("click",loadDetailChart);

    // column labels
    var column = g.selectAll(".column")
        .data(data.matrix)
        .enter().append("g")
          .attr("class", "column")
          .attr("transform", function(d, i) { return "translate(" + x(i) + ")rotate(-90)"; });

    column.append("line")
        .attr("x1", -xlen);

    column.append("text")
        .attr("x", 6)
        .attr("y", x.rangeBand() / 2)
        .attr("dy", ".32em")
        .attr("text-anchor", "start")
        .attr("class","matrixlabel")
        .text(function(d, i) { return names[i]; });
    
    function drawrow(row) {
        var cell = d3.select(this).selectAll(".cell")
            .data(row.filter(function(d) { return d.z; }))
            .enter().append("g")
              .attr("class","cell")
              .on("click",loadDetailChart);
        cell.append("rect")
              .attr("x", function(d) { return x(d.x); })
              .attr("width", x.rangeBand())
              .attr("height", x.rangeBand())
              .style("fill", function(d) { return c(d.z);});
        cell.append("text")
              .text(function(d, i) { return d.z.toFixed(2); })
              .attr("x", function(d) { return x(d.x)+2; })
              .attr("y",x.rangeBand()/2+2)
              .attr("class","cellLabel");
        cell.on("mouseover", mouseover)
            .on("mouseout", mouseout);
    }
    function loadDetailChart(d,i){
    	var elem = d3.select(this); 
    	var params = {startDate:data.date, 
    			      endDate:data.date,
    			      run: true};
    	if(elem.classed("matrixlabel")){ // clicked on label
    		params.visType = "dygraph.json";
    		params.selectedSeries = [];
    		d.forEach(function(entry){params.selectedSeries.push(ids[entry.x]);});    			
    	}else{ // clicked on cell		      
    		params.visType = "dygraph-ratios.json";
    		params.selectedSeries = [ids[d.x], ids[d.y]];
    	}
    	window.open(baseUrl+'/series-of/'+plantId+'?params='+JSON.stringify(params));
    }

    function mouseover(p) {
        svg.selectAll(".row text.matrixlabel").classed("active", function(d, i) { return i == p.y; });
        svg.selectAll(".column text.matrixlabel").classed("active", function(d, i) { return i == p.x; });
        svg.selectAll("text.cellLabel").classed("active", function(d, i) { return d.x==p.x && d.y == p.y; });
        svg.selectAll("text.problabel").classed("active", function(d, i) { return i==p.y; });
        summary.text("Wahrscheinlichkeit einer Verhaltensänderung von \""+names[p.y]+"\": "+(data.probabilities[p.y] * 100).toFixed(1)+"%%");
    }

    function mouseout() {
        d3.selectAll("text").classed("active", false);
    }
    
    
     function redraw(){
    	var n = +this.value || this.selectedIndex;
        var day=days[n];
        if(!day) return;        
        
        data = populate(day);
    	var rows = svg.selectAll(".row").data(data.matrix);
    	
    	rows.selectAll('.cell rect').style("fill", function(d) { return  c(data.matrix[d.x][d.y].z);});
    	rows.selectAll('.cell text.cellLabel').text(function(d) { return data.matrix[d.x][d.y].z.toFixed(2); });
    	svg.select(".datelabel").text(data.date);
    	svg.selectAll("text.problabel").data(data.probabilities).text(function(d){return (d*100).toFixed(0)+"%%";});
    }

});

}( window.EntropyChart = window.EntropyChart || {}, jQuery, "%s", "%s", "%s", "%s"));