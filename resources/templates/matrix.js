(function (EntropyChart, $, baseUrl, selector, plantId, undefined){

var margin = {top: 200, right: 100, bottom: 10, left: 200},
    width = height = 400;
var x = d3.scale.ordinal().rangeBands([0, width]),
    c = d3.scale.linear().domain([0,0.001,1,3]).range(["red","green","yellow","red"]);

var svg = d3.select(selector)
            .append("svg")
              .attr("width", width + margin.left + margin.right)
              .attr("height", height + margin.top + margin.bottom);
var g = svg.append("g")
                .attr("transform", "translate(" + margin.left + "," + margin.top + ")");
var slider = d3.select(selector)
               .append("input")
               .attr("type", "number")
               .attr("min",0)
               .attr("value",0);

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

d3.json(baseUrl+"/data/"+plantId+"/entropy-bulk.json", function(json) {
    var names = json.names,
        n = names.length,
        days = json.days;

    var data = populate(days[0]);
    
    x.domain(d3.range(n));

    g.append("rect")
        .attr("class", "background")
        .attr("width", width)
        .attr("height", height);

    svg.append("text")
       .attr("x", 20)
       .attr("y", 20)
       .attr("class","datelabel")
       .text(data.date)
       .style("font-weight", "bold")
       .style("font-size", "large");

    g.selectAll("text.problabel").data(data.probabilities)
       .enter()
       .append("text")
         .attr("class","problabel")
         .attr("x",width+x.rangeBand())
         .attr("y", function(d,i){return x(i)+x.rangeBand()/2;})
         .attr("dy", ".32em")
         .text(function(d){return (d*100).toFixed(0)+"%%";});
    
    var rows = g.selectAll(".row")
        .data(data.matrix);
        
    var row = rows.enter().append("g")
          .attr("class", "row")
          .attr("transform", function(d, i) { return "translate(0," + x(i) + ")"; })
          .each(drawrow);
    
    row.append("line")
        .attr("x2", width);

    row.append("text")
        .attr("x", -6)
        .attr("y", x.rangeBand() / 2)
        .attr("dy", ".32em")
        .attr("text-anchor", "end")
        .attr("class","matrixlabel")
        .text(function(d, i) { return names[i]; });

    var column = g.selectAll(".column")
        .data(data.matrix)
        .enter().append("g")
          .attr("class", "column")
          .attr("transform", function(d, i) { return "translate(" + x(i) + ")rotate(-90)"; });

    column.append("line")
        .attr("x1", -width);

    column.append("text")
        .attr("x", 6)
        .attr("y", x.rangeBand() / 2)
        .attr("dy", ".32em")
        .attr("text-anchor", "start")
        .attr("class","matrixlabel")
        .text(function(d, i) { return names[i]; });

    
    slider.data(d3.range(n))
          .attr("max",days.length)
          .attr("value",0)
          .on("change", redraw);
    
    function drawrow(row) {
        var cell = d3.select(this).selectAll(".cell")
            .data(row.filter(function(d) { return d.z; }))
            .enter().append("g")
              .attr("class","cell");
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

    function mouseover(p) {
        d3.selectAll(".row text.matrixlabel").classed("active", function(d, i) { return i == p.y; });
        d3.selectAll(".column text.matrixlabel").classed("active", function(d, i) { return i == p.x; });
        d3.selectAll("text.cellLabel").classed("active", function(d, i) { return d.x==p.x && d.y == p.y; });
        d3.selectAll("text.problabel").classed("active", function(d, i) { return i==p.y; });
        $('#entropyText').text("Wahrscheinlichkeit einer Verhaltensänderung von \""+names[p.y]+"\": "+(data.probabilities[p.y] * 100).toFixed(1)+"%%");
    }

    function mouseout() {
        d3.selectAll("text").classed("active", false);
    }
    
    
     function redraw(){
    	var n = +this.value;
        var day=days[n];
        if(!day) return;        
        
        data = populate(day);
    	var rows = svg.selectAll(".row").data(data.matrix);
    	
    	rows.selectAll('.cell rect').style("fill", function(d) { return  c(data.matrix[d.x][d.y].z);});
    	rows.selectAll('.cell text.cellLabel').text(function(d) { return data.matrix[d.x][d.y].z.toFixed(2); });
    	svg.select(".datelabel").text(data.date);
    	svg.selectAll("text.problabel").data(data.probabilities).text(function(d){return (d*100).toFixed(0)+"%%";});
    };    

});

}( window.EntropyChart = window.EntropyChart || {}, jQuery, "%s", "%s", "%s"));